package com.chronos.delivery;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class CustomerLicense {
	private static final Logger LOG = LoggerFactory.getLogger(CustomerLicense.class);
	private final JdbcTemplate jdbc;
	private final TransactionTemplate transactions;
	private final LicenseDocument verifier;
	private final CustomerDelegation delegation;
	private final Path file;
	private final Path trustedKeys;
	private final String instance;
	private final String administrator;
	private final String displayName;
	private final String password;
	private final String mode;
	private volatile LicenseDocument.Payload active;
	private volatile String diagnostic = "等待加载授权";

	CustomerLicense(JdbcTemplate jdbc, TransactionTemplate transactions, LicenseDocument verifier,
			CustomerDelegation delegation,
			Environment environment,
			@Value("${chronos.delivery.deployment-id:}") String instance,
			@Value("${chronos.delivery.license-path:/etc/chronos/chronos-license.json}") String file,
			@Value("${chronos.delivery.public-keys:/etc/chronos/trusted-license-keys}") String trustedKeys,
			@Value("${chronos.delivery.group-admin:}") String administrator,
			@Value("${chronos.delivery.group-name:客户集团}") String displayName,
			@Value("${chronos.delivery.group-password:}") String password) {
		this.jdbc = jdbc;
		this.transactions = transactions;
		this.verifier = verifier;
		this.delegation = delegation;
		this.mode = DeliveryMode.resolve(environment);
		this.instance = instance;
		this.administrator = administrator;
		this.displayName = displayName;
		this.password = password;
		this.file = Path.of(file);
		this.trustedKeys = Path.of(trustedKeys.isBlank() ? "/etc/chronos/trusted-license-keys" : trustedKeys);
		if (!List.of("standard", "issuer", "customer").contains(mode)) {
			throw new IllegalArgumentException("无效的 Chronos 交付运行模式");
		}
	}

	record Status(String mode, boolean active, String deploymentId, UUID licenseId,
			int revision, List<String> modules, List<String> routes, Map<String, String> productNames, String message) {
	}

	boolean customer() {
		return "customer".equals(mode);
	}

	boolean issuer() {
		return "issuer".equals(mode);
	}

	Status status() {
		var current = active;
		return new Status(mode, current != null, instance, current == null ? null : current.licenseId(),
				current == null ? 0 : current.revision(), current == null ? List.of() : current.modules(),
				java.util.stream.Stream.concat(products().stream().flatMap(product -> product.menus().stream()),
						modelAdministrationAllowed() ? java.util.stream.Stream.of("/admin/ai-models")
								: java.util.stream.Stream.empty()).distinct().toList(),
				products().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
						DeliveryCatalog.Product::code, DeliveryCatalog.Product::name)), diagnostic);
	}

	List<DeliveryCatalog.Product> products() {
		var current = active;
		return current == null ? List.of() : DeliveryCatalog.PRODUCTS.stream()
				.filter(product -> current.modules().contains(product.code())).toList();
	}

	boolean moduleAllowed(String code) {
		return !customer() || products().stream().anyMatch(product -> product.code().equals(code));
	}

	boolean modelAdministrationAllowed() {
		return customer() && (moduleAllowed("AI_KNOWLEDGE") || moduleAllowed("EDU_OPERATIONS_AGENT"));
	}

	@EventListener(ApplicationReadyEvent.class)
	public void initialize() {
		if (!customer()) return;
		try {
			UUID deployment = UUID.fromString(instance);
			// Serialize customer initialization across instances sharing the deployment database.
			active = transactions.execute(status -> {
				jdbc.execute("LOCK TABLE cd_local_license IN EXCLUSIVE MODE");
				var records = jdbc.queryForList("SELECT document,revision,deployment_id FROM cd_local_license");
				if (!records.isEmpty()) {
					var stored = records.getFirst();
					if (!deployment.equals(stored.get("deployment_id"))) {
						throw new IllegalStateException("数据库实例 ID 与部署配置不匹配");
					}
					active = verifier.verify((String) stored.get("document"), deployment, trustedKeys);
				}
				LicenseDocument.Payload candidate;
				String document;
				try {
					document = LicenseDocument.readDocument(file);
					candidate = verifier.verify(document, deployment, trustedKeys);
				} catch (Exception failure) {
					diagnostic = "挂载授权文件缺失、无效或与目标部署不匹配";
					return active;
				}
				if (active != null) {
					if (candidate.revision() == active.revision() && candidate.licenseId().equals(active.licenseId())) {
						diagnostic = "授权已生效";
						return active;
					}
					if (candidate.revision() <= active.revision() || !Set.copyOf(candidate.modules()).containsAll(active.modules())) {
						diagnostic = "授权版本过旧、冲突或丢失已买断模块";
						return active;
					}
				}
				delegation.initialize(administrator, displayName, password, candidate.modules());
				jdbc.update("""
						INSERT INTO cd_local_license(singleton,deployment_id,revision,document) VALUES (true,?,?,?)
						ON CONFLICT (singleton) DO UPDATE SET revision=EXCLUDED.revision,document=EXCLUDED.document,
						applied_at=now()
						""", deployment, candidate.revision(), document);
				diagnostic = "授权已生效";
				return candidate;
			});
		} catch (RuntimeException failure) {
			diagnostic = "授权初始化失败，请检查部署标识、可信公钥及集团管理员配置";
			LOG.warn("客户授权初始化失败 ({})", failure.getClass().getSimpleName());
		}
	}
}
