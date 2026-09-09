package com.chronos.message.storage;

public interface PublicationFileStorage {
	void store(String key, byte[] content, String contentType);

	byte[] read(String key);

	void delete(String key);
}
