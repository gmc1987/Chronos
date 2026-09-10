package com.chronos.file.storage;

public interface ManagedFileStorage {
	void store(String key, byte[] content, String contentType);
	byte[] read(String key);
	void delete(String key);
}
