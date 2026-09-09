package com.chronos.message.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
		name = "chronos.message.storage-type",
		havingValue = "LOCAL",
		matchIfMissing = true)
public class LocalPublicationFileStorage implements PublicationFileStorage {
	private final Path root;

	public LocalPublicationFileStorage(
			@Value("${chronos.message.storage-path:./data/publications}") String storagePath) {
		this.root = Path.of(storagePath).toAbsolutePath().normalize();
	}

	@Override
	public void store(String key, byte[] content, String contentType) {
		try {
			Path path = resolve(key);
			Files.createDirectories(path.getParent());
			Files.write(path, content);
		} catch (IOException exception) {
			throw new IllegalStateException("附件写入失败", exception);
		}
	}

	@Override
	public byte[] read(String key) {
		try {
			Path path = resolve(key);
			if (!Files.exists(path)) {
				throw new IllegalArgumentException("附件文件不存在");
			}
			return Files.readAllBytes(path);
		} catch (IOException exception) {
			throw new IllegalStateException("附件读取失败", exception);
		}
	}

	@Override
	public void delete(String key) {
		try {
			Files.deleteIfExists(resolve(key));
		} catch (IOException exception) {
			throw new IllegalStateException("附件删除失败", exception);
		}
	}

	private Path resolve(String key) {
		Path path = root.resolve(key).normalize();
		if (!path.startsWith(root)) {
			throw new IllegalArgumentException("非法文件路径");
		}
		return path;
	}
}
