package com.chronos.integration.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import com.chronos.security.SecretEncryptionProvider;

class FailClosedPlatformSecretCipherTest {
	@Test
	void refusesEncryptionUntilPlatformProviderIsConfigured() {
		FailClosedPlatformSecretCipher cipher = new FailClosedPlatformSecretCipher();

		assertThrows(IllegalStateException.class, () -> cipher.encrypt("secret"));
		assertThrows(IllegalStateException.class, () -> cipher.decrypt("ciphertext"));
	}

	@Test
	void configuredProviderDelegatesToPlatformEncryption() {
		SecretEncryptionProvider platform = new SecretEncryptionProvider() {
			@Override public String encrypt(String plaintext) { return "v1:" + plaintext; }
			@Override public String decrypt(String ciphertext) { return ciphertext.substring(3); }
			@Override public String keyVersion() { return "test"; }
			@Override public String fingerprint(String plaintext) { return "fingerprint"; }
		};
		ConfiguredPlatformSecretCipher cipher = new ConfiguredPlatformSecretCipher(platform);

		assertEquals("v1:secret", cipher.encrypt("secret"));
		assertEquals("secret", cipher.decrypt("v1:secret"));
	}
}
