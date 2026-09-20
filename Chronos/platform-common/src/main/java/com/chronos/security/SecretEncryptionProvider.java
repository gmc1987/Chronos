package com.chronos.security;

/**
 * Platform encryption boundary for values that must never be persisted in
 * plaintext. Implementations must fail closed when their key provider is not
 * configured.
 */
public interface SecretEncryptionProvider {
	String encrypt(String plaintext);

	String decrypt(String ciphertext);

	String keyVersion();

	String fingerprint(String plaintext);
}
