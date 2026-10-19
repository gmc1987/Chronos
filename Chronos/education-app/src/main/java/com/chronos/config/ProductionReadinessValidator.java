package com.chronos.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails a production-profile startup before traffic is accepted when Chronos is
 * still using a development secret, local dependency or unsafe transport.
 */
@Component
@ConditionalOnProperty(
		name = "chronos.production-readiness.enabled",
		havingValue = "true")
public class ProductionReadinessValidator implements InitializingBean {
	private final Environment environment;

	public ProductionReadinessValidator(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void afterPropertiesSet() {
		validate();
	}

	void validate() {
		List<String> problems = new ArrayList<>();
		requireExact(problems,
				"chronos.production-readiness.deployment-acknowledgement",
				"PRODUCTION",
				"CHRONOS_PRODUCTION_ACK must be PRODUCTION");
		requireSecret(problems, "security.jwt.secret", 32, "JWT secret");
		validateEncryptionKey(problems);
		validateDatabase(problems);
		validateRedis(problems);
		validateHttps(problems, "minio.endpoint", "MinIO endpoint");
		requireSecret(problems, "minio.accessKey", 8, "MinIO access key");
		requireSecret(problems, "minio.secretKey", 16, "MinIO secret key");
		rejectValue(problems, "minio.accessKey", "minioadmin", "MinIO access key");
		rejectValue(problems, "minio.secretKey", "minioadmin", "MinIO secret key");
		requireExact(problems, "chronos.file.storage-type", "MINIO",
				"file storage must use MINIO");
		requireExact(problems, "chronos.message.storage-type", "MINIO",
				"message storage must use MINIO");
		requireExact(problems, "spring.jpa.hibernate.ddl-auto", "validate",
				"Hibernate ddl-auto must be validate");
		requireExact(problems, "flowable.database-schema-update", "false",
				"Flowable schema updates must be disabled");
		validateCors(problems);
		if (!problems.isEmpty()) {
			throw new IllegalStateException(
					"Production readiness validation failed:\n - "
							+ String.join("\n - ", problems));
		}
	}

	private void validateDatabase(List<String> problems) {
		String value = property("spring.datasource.url");
		if (!value.startsWith("jdbc:postgresql://")) {
			problems.add("database URL must use PostgreSQL");
			return;
		}
		String host = value.substring("jdbc:postgresql://".length()).split("[:/]", 2)[0];
		if (localHost(host)) {
			problems.add("database host must not be loopback in production");
		}
		requireSecret(problems, "spring.datasource.password", 12, "database password");
		rejectValue(problems, "spring.datasource.password", "Chronos123", "database password");
	}

	private void validateRedis(List<String> problems) {
		String host = property("spring.data.redis.host");
		if (host.isBlank() || localHost(host)) {
			problems.add("Redis host must be explicitly configured and non-loopback");
		}
	}

	private void validateEncryptionKey(List<String> problems) {
		String value = property("chronos.security.encryption-key");
		try {
			int length = Base64.getDecoder().decode(value).length;
			if (length != 16 && length != 24 && length != 32) {
				problems.add("encryption key must decode to 16, 24 or 32 bytes");
			}
		} catch (IllegalArgumentException exception) {
			problems.add("encryption key must be valid Base64");
		}
	}

	private void validateCors(List<String> problems) {
		String value = property("security.cors.allowed-origin-patterns");
		String normalized = value.toLowerCase(Locale.ROOT);
		if (value.isBlank() || value.contains("*") || normalized.contains("localhost")
				|| normalized.contains("127.0.0.1") || normalized.contains("http://")) {
			problems.add("CORS origins must be explicit production HTTPS origins");
		}
	}

	private void validateHttps(List<String> problems, String key, String label) {
		try {
			URI uri = URI.create(property(key));
			if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
					|| localHost(uri.getHost())) {
				problems.add(label + " must use a non-loopback HTTPS URL");
			}
		} catch (IllegalArgumentException exception) {
			problems.add(label + " is invalid");
		}
	}

	private void requireSecret(List<String> problems, String key, int minimumLength, String label) {
		if (property(key).length() < minimumLength) {
			problems.add(label + " must contain at least " + minimumLength + " characters");
		}
	}

	private void rejectValue(List<String> problems, String key, String rejected, String label) {
		if (rejected.equals(property(key))) {
			problems.add(label + " still uses a development default");
		}
	}

	private void requireExact(List<String> problems, String key, String expected, String message) {
		if (!expected.equalsIgnoreCase(property(key))) {
			problems.add(message);
		}
	}

	private String property(String key) {
		return environment.getProperty(key, "").trim();
	}

	private boolean localHost(String host) {
		return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
				|| "::1".equals(host) || "0.0.0.0".equals(host);
	}
}
