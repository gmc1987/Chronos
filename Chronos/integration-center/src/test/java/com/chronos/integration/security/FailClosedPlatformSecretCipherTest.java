package com.chronos.integration.security;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FailClosedPlatformSecretCipherTest {
	@Test
	void refusesEncryptionUntilPlatformProviderIsConfigured() {
		FailClosedPlatformSecretCipher cipher = new FailClosedPlatformSecretCipher();

		assertThrows(IllegalStateException.class, () -> cipher.encrypt("secret"));
		assertThrows(IllegalStateException.class, () -> cipher.decrypt("ciphertext"));
	}
}
