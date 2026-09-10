package com.chronos.file.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 使用真实 MinIO 验证对象存储协议。
 *
 * <p>该测试默认关闭，避免普通单元测试依赖外部服务；显式设置
 * {@code CHRONOS_MINIO_INTEGRATION_TEST=true} 后才会执行。</p>
 */
@EnabledIfEnvironmentVariable(
		named = "CHRONOS_MINIO_INTEGRATION_TEST",
		matches = "true")
class MinioManagedFileStorageIntegrationTest {
	@Test
	void storesReadsAndDeletesObjectUsingProductionStorageAdapter() {
		String endpoint = required("CHRONOS_MINIO_ENDPOINT");
		String accessKey = required("CHRONOS_MINIO_ACCESS_KEY");
		String secretKey = required("CHRONOS_MINIO_SECRET_KEY");
		String bucket = environment(
				"CHRONOS_FILE_MINIO_BUCKET",
				"chronos-education-files");
		String objectKey = "integration-test/" + UUID.randomUUID() + ".txt";
		byte[] expected = "Chronos MinIO integration verification"
				.getBytes(StandardCharsets.UTF_8);
		MinioManagedFileStorage storage = new MinioManagedFileStorage(
				endpoint,
				accessKey,
				secretKey,
				bucket);

		try {
			storage.store(objectKey, expected, "text/plain");
			assertThat(storage.read(objectKey)).isEqualTo(expected);
		} finally {
			// 无论断言是否成功都清理临时对象，避免验收数据污染业务桶。
			storage.delete(objectKey);
		}
		assertThatThrownBy(() -> storage.read(objectKey))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("MinIO 文件读取失败");
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
