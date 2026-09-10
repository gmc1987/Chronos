package com.chronos.file.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "chronos.file.storage-type", havingValue = "MINIO")
public class MinioManagedFileStorage implements ManagedFileStorage {
	private final MinioClient client;
	private final String bucket;

	public MinioManagedFileStorage(
			@Value("${minio.endpoint}") String endpoint,
			@Value("${minio.accessKey}") String accessKey,
			@Value("${minio.secretKey}") String secretKey,
			@Value("${chronos.file.minio-bucket:chronos-files}") String bucket) {
		this.client = MinioClient.builder()
				.endpoint(endpoint)
				.credentials(accessKey, secretKey)
				.build();
		this.bucket = bucket;
	}

	@Override
	public void store(String key, byte[] content, String contentType) {
		try {
			ensureBucket();
			client.putObject(PutObjectArgs.builder()
					.bucket(bucket)
					.object(key)
					.contentType(contentType)
					.stream(new ByteArrayInputStream(content), content.length, -1)
					.build());
		} catch (Exception exception) {
			throw new IllegalStateException("MinIO 文件写入失败", exception);
		}
	}

	@Override
	public byte[] read(String key) {
		try (var input = client.getObject(GetObjectArgs.builder()
				.bucket(bucket)
				.object(key)
				.build())) {
			return input.readAllBytes();
		} catch (Exception exception) {
			throw new IllegalStateException("MinIO 文件读取失败", exception);
		}
	}

	@Override
	public void delete(String key) {
		try {
			client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
		} catch (Exception exception) {
			throw new IllegalStateException("MinIO 文件删除失败", exception);
		}
	}

	private void ensureBucket() throws Exception {
		if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
			client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
		}
	}
}
