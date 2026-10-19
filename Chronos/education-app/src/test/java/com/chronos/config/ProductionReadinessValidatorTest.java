package com.chronos.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionReadinessValidatorTest {
	@Test
	void rejectsDevelopmentDefaults() {
		MockEnvironment environment = validEnvironment()
				.withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/Chronos")
				.withProperty("minio.secretKey", "minioadmin");
		IllegalStateException error = assertThrows(
				IllegalStateException.class,
				() -> validate(environment));
		assertTrue(error.getMessage().contains("loopback"));
		assertTrue(error.getMessage().contains("MinIO secret key"));
	}

	@Test
	void acceptsExplicitProductionConfiguration() {
		assertDoesNotThrow(() -> validate(validEnvironment()));
	}

	private void validate(MockEnvironment environment) {
		new ProductionReadinessValidator(environment)
				.validate();
	}

	private MockEnvironment validEnvironment() {
		MockEnvironment environment = new MockEnvironment();
		Map.ofEntries(
				Map.entry("chronos.production-readiness.deployment-acknowledgement", "PRODUCTION"),
				Map.entry("security.jwt.secret", "a-production-jwt-secret-longer-than-32"),
				Map.entry("chronos.security.encryption-key", "MDEyMzQ1Njc4OWFiY2RlZg=="),
				Map.entry("spring.datasource.url", "jdbc:postgresql://postgres.internal:5432/chronos"),
				Map.entry("spring.datasource.password", "database-production-password"),
				Map.entry("spring.data.redis.host", "redis.internal"),
				Map.entry("minio.endpoint", "https://objects.example.edu"),
				Map.entry("minio.accessKey", "production-access"),
				Map.entry("minio.secretKey", "production-secret-value"),
				Map.entry("chronos.file.storage-type", "MINIO"),
				Map.entry("chronos.message.storage-type", "MINIO"),
				Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
				Map.entry("flowable.database-schema-update", "false"),
				Map.entry("security.cors.allowed-origin-patterns", "https://portal.example.edu"))
				.forEach(environment::setProperty);
		return environment;
	}
}
