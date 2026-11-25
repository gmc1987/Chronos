package com.chronos.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

@Component
@Conditional(DeliveryMode.IssuerCondition.class)
class LocalDeliveryBuilds {
	private static final Logger log = LoggerFactory.getLogger(LocalDeliveryBuilds.class);
	private static final String SCRIPT = "scripts/build-local-delivery-package.sh";
	private static final Duration TIMEOUT = Duration.ofHours(3);

	private final JdbcTemplate jdbc;
	private final ObjectMapper mapper;
	private final LicenseDocument licenses;
	private final Path source;
	private final Path artifacts;
	private final Path publicKeys;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(
			Thread.ofPlatform().daemon().name("chronos-local-delivery").factory());
	private Future<?> nextBuild;

	LocalDeliveryBuilds(JdbcTemplate jdbc, ObjectMapper mapper, LicenseDocument licenses,
			@Value("${chronos.delivery.source-dir:}") String source,
			@Value("${chronos.delivery.artifact-dir:./data/delivery}") String artifacts,
			@Value("${chronos.delivery.public-keys:}") String publicKeys,
			@Value("${chronos.delivery.signing-key:}") String signingKey) {
		this.jdbc = jdbc;
		this.mapper = mapper;
		this.licenses = licenses;
		this.source = source.isBlank() ? findWorkspace() : absolute(source);
		this.artifacts = absolute(artifacts);
		this.publicKeys = !publicKeys.isBlank() ? absolute(publicKeys)
				: signingKey.isBlank() ? absolute("./data/trusted-license-keys")
				: absolute(signingKey).getParent().resolve("trusted-keys");
	}

	@Scheduled(fixedDelay = 2000)
	void poll() {
		if (!executor.isShutdown() && (nextBuild == null || nextBuild.isDone())) {
			nextBuild = executor.submit(this::runNext);
		}
	}

	@PreDestroy
	void stop() throws InterruptedException {
		executor.shutdownNow();
		executor.awaitTermination(10, TimeUnit.SECONDS);
	}

	private void runNext() {
		try {
			var job = claim();
			if (!job.isEmpty()) {
				execute(job.getFirst());
			}
		} catch (Exception error) {
			log.error("本地交付打包任务调度失败", error);
		}
	}

	private List<Job> claim() {
		jdbc.update("""
				UPDATE cd_package_job SET status='FAILED', phase='构建中断',
				    error='本地构建已中断，请重新打包', updated_at=now()
				WHERE status='RUNNING' AND updated_at < now()-interval '2 minutes'
				""");
		UUID token = UUID.randomUUID();
		return jdbc.query("""
				WITH pending AS (
				    SELECT id FROM cd_package_job WHERE status='QUEUED' ORDER BY created_at, id
				    LIMIT 1 FOR UPDATE SKIP LOCKED
				), building AS (
				    UPDATE cd_package_job j SET status='RUNNING', phase='准备本地构建',
				        worker_token=?, attempts=attempts+1, updated_at=now()
				    FROM pending p WHERE p.id=j.id RETURNING j.*
				)
				SELECT j.id,j.deployment_id,j.license_revision,c.config,l.document
				FROM building j JOIN cd_deployment_config c ON c.deployment_id=j.deployment_id AND c.revision=j.config_revision
				JOIN cd_license l ON l.deployment_id=j.deployment_id AND l.revision=j.license_revision
				""", (rs, index) -> new Job(UUID.fromString(rs.getString("id")), token,
						UUID.fromString(rs.getString("deployment_id")), rs.getInt("license_revision"),
						rs.getString("config"), rs.getString("document")), token);
	}

	private void execute(Job job) {
		Path control = null;
		Process process = null;
		Path output = artifacts.resolve(job.id() + "-" + job.token() + ".log");
		try {
			if (source == null || !Files.isRegularFile(source.resolve(SCRIPT))) {
				throw new IllegalStateException("找不到本地构建脚本，请设置 CHRONOS_DELIVERY_SOURCE 指向 Chronos 项目根目录");
			}
			Files.createDirectories(artifacts);
			control = Files.createTempDirectory(artifacts, ".build-" + job.id() + "-");
			mapper.writeValue(control.resolve("job.json").toFile(), Map.of(
					"id", job.id(), "token", job.token(), "instance", job.instance(), "licenseRevision", job.licenseRevision(),
					"config", mapper.readTree(job.config()), "license", mapper.readTree(job.license())));
			var builder = new ProcessBuilder("/bin/bash", source.resolve(SCRIPT).toString(), control.toString())
					.directory(source.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());
			// The build needs tool settings and public keys only. Do not pass issuer or database credentials.
			var env = builder.environment();
			env.keySet().retainAll(List.of("PATH", "HOME", "LANG", "LC_ALL", "TMPDIR", "JAVA_HOME",
					"DOCKER_HOST", "DOCKER_CONTEXT", "DOCKER_CONFIG", "HTTP_PROXY", "HTTPS_PROXY", "NO_PROXY",
					"CHRONOS_DELIVERY_PYTHON", "CHRONOS_DELIVERY_DOCKER"));
			env.putIfAbsent("JAVA_HOME", System.getProperty("java.home"));
			env.put("PATH", System.getProperty("java.home") + "/bin:" + env.getOrDefault("PATH", "/usr/bin:/bin"));
			env.put("CHRONOS_DELIVERY_PUBLIC_KEYS", publicKeys.toString());
			process = builder.start();
			log.info("本地打包任务 {} 开始，日志 {}", job.id(), output);

			awaitResult(job, control, process);
		} catch (InterruptedException error) {
			terminate(process);
			fail(job, "本地应用停止，打包已中断，可手动重试");
			Thread.currentThread().interrupt();
		} catch (Exception error) {
			terminate(process);
			log.warn("本地打包任务 {} 失败，日志 {}", job.id(), output, error);
			fail(job, error instanceof IllegalStateException ? error.getMessage() : "无法完成本地打包，请查看任务日志");
		} finally {
			if (control != null) {
				try {
					FileSystemUtils.deleteRecursively(control);
				} catch (IOException error) {
					log.warn("无法清理本地打包临时目录 {}", control, error);
				}
			}
		}
	}

	private void awaitResult(Job job, Path control, Process process) throws IOException, InterruptedException {
		Path progress = control.resolve("progress.json");
		long started = System.nanoTime();
		String phase = "准备本地构建";
		int ticks = 0;
		while (!process.waitFor(2, TimeUnit.SECONDS)) {
			if (System.nanoTime() - started >= TIMEOUT.toNanos()) {
				throw new IllegalStateException("本地打包超时，请查看任务日志");
			}
			String current = readProgress(progress).path("phase").asText("");
			if (!current.isBlank() && !current.equals(phase)) {
				phase = current;
				heartbeat(job, phase);
				log.info("本地打包任务 {} · {}", job.id(), phase);
				ticks = 0;
			} else if (++ticks == 8) {
				heartbeat(job, phase);
				ticks = 0;
			}
		}

		var result = readProgress(progress);
		if (process.exitValue() != 0) {
			throw new IllegalStateException(result.path("error").asText("本地构建失败，请查看任务日志"));
		}
		String digest = result.path("sha256").asText("");
		if (!digest.matches("[a-f0-9]{64}") || !result.path("manifest").isObject()) {
			throw new IllegalStateException("本地构建脚本未生成完整的打包校验信息");
		}
		var bundle = control.resolve("chronos-delivery");
		try {
			if (licenses.verify(LicenseDocument.readDocument(bundle.resolve("chronos-license.json")),
					job.instance(), bundle.resolve("trusted-license-keys")).revision() != job.licenseRevision()) {
				throw new IllegalStateException("部署包内授权版本与任务不一致");
			}
		} catch (IllegalArgumentException error) {
			throw new IllegalStateException("部署包内授权与公钥不匹配，请核对 CHRONOS_DELIVERY_PUBLIC_KEYS", error);
		}
		heartbeat(job, "保存部署包");
		for (String name : List.of(job.id() + ".tar.gz", job.id() + ".tar.gz.sha256")) {
			Files.move(control.resolve(name), artifacts.resolve(name), StandardCopyOption.REPLACE_EXISTING);
		}
		jdbc.update("""
				UPDATE cd_package_job SET status='READY', phase='完成', archive_sha256=?, image_manifest=?,
				    updated_at=now() WHERE id=? AND worker_token=? AND status='RUNNING'
				""", digest, result.path("manifest").path("images").toString(), job.id(), job.token());
		log.info("本地打包任务 {} 完成", job.id());
	}

	private JsonNode readProgress(Path progress) throws IOException {
		return Files.isRegularFile(progress) ? mapper.readTree(progress.toFile()) : mapper.createObjectNode();
	}

	private void heartbeat(Job job, String phase) {
		int changed = jdbc.update("""
				UPDATE cd_package_job SET phase=?, updated_at=now()
				WHERE id=? AND worker_token=? AND status='RUNNING'
				""", phase, job.id(), job.token());
		if (changed != 1) {
			throw new IllegalStateException("打包任务已结束或被其他构建进程接管");
		}
	}

	private void fail(Job job, String message) {
		jdbc.update("""
				UPDATE cd_package_job SET status='FAILED', error=?, updated_at=now()
				WHERE id=? AND worker_token=? AND status='RUNNING'
				""", message.substring(0, Math.min(message.length(), 500)), job.id(), job.token());
	}

	private static void terminate(Process process) {
		if (process != null && process.isAlive()) {
			process.descendants().forEach(ProcessHandle::destroyForcibly);
			process.destroyForcibly();
		}
	}

	private static Path findWorkspace() {
		for (Path directory = absolute("."); directory != null; directory = directory.getParent()) {
			if (Files.isRegularFile(directory.resolve(SCRIPT))) {
				return directory;
			}
		}
		return null;
	}

	private static Path absolute(String path) {
		return Path.of(path).toAbsolutePath().normalize();
	}

	private record Job(UUID id, UUID token, UUID instance, int licenseRevision, String config, String license) {
	}
}
