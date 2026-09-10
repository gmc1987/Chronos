package com.chronos.file.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "chronos.file.storage-type", havingValue = "LOCAL", matchIfMissing = true)
public class LocalManagedFileStorage implements ManagedFileStorage {
	private final Path root;

	public LocalManagedFileStorage(@Value("${chronos.file.storage-path:./data/files}") String root) {
		this.root = Path.of(root).toAbsolutePath().normalize();
	}

	@Override
	public void store(String key, byte[] content, String contentType) {
		Path temporary = null;
		try {
			Path target = resolve(key);
			Files.createDirectories(target.getParent());
			temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
			Files.write(temporary, content);
			moveAtomically(temporary, target);
		} catch (Exception exception) {
			deleteTemporary(temporary, exception);
			throw new IllegalStateException("文件写入失败", exception);
		}
	}

	@Override
	public byte[] read(String key) {
		try {
			return Files.readAllBytes(resolve(key));
		} catch (Exception exception) {
			throw new IllegalStateException("文件读取失败", exception);
		}
	}

	@Override
	public void delete(String key) {
		try {
			Files.deleteIfExists(resolve(key));
		} catch (Exception exception) {
			throw new IllegalStateException("文件删除失败", exception);
		}
	}

	private Path resolve(String key) {
		Path target = root.resolve(key).normalize();
		if (!target.startsWith(root)) {
			throw new IllegalArgumentException("非法文件路径");
		}
		return target;
	}

	private void moveAtomically(Path source, Path target) throws Exception {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (java.nio.file.AtomicMoveNotSupportedException exception) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void deleteTemporary(Path temporary, Exception original) {
		if (temporary == null) {
			return;
		}
		try {
			Files.deleteIfExists(temporary);
		} catch (Exception cleanupFailure) {
			original.addSuppressed(cleanupFailure);
		}
	}
}
