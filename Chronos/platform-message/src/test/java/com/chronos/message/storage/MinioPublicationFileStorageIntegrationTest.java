package com.chronos.message.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 验证通知公告附件使用的真实 MinIO 适配器。
 *
 * <p>只有显式开启集成测试时才访问外部 MinIO，普通构建不依赖本地服务。</p>
 */
@EnabledIfEnvironmentVariable(
		named = "CHRONOS_MINIO_INTEGRATION_TEST",
		matches = "true")
class MinioPublicationFileStorageIntegrationTest {
	@Test
	void storesReadsAndDeletesPublicationAttachment() {
		String objectKey = "integration-test/" + UUID.randomUUID() + ".txt";
		byte[] expected = "Chronos publication attachment verification"
				.getBytes(StandardCharsets.UTF_8);
		MinioPublicationFileStorage storage = new MinioPublicationFileStorage(
				required("CHRONOS_MINIO_ENDPOINT"),
				required("CHRONOS_MINIO_ACCESS_KEY"),
				required("CHRONOS_MINIO_SECRET_KEY"),
				environment(
						"CHRONOS_MESSAGE_MINIO_BUCKET",
						"chronos-education-publications"));

		try {
			storage.store(objectKey, expected, "text/plain");
			assertThat(storage.read(objectKey)).isEqualTo(expected);
		} finally {
			storage.delete(objectKey);
		}
		assertThatThrownBy(() -> storage.read(objectKey))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("MinIO附件读取失败");
	}

	private String required(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("缺少环境变量：" + name);
		}
		return value;
	}

	private String environment(String name, String fallback) {
		String value = System.getenv(name);
		return value == null || value.isBlank() ? fallback : value;
	}
}
