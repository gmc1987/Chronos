package com.chronos.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class AiCredentialSecurityTest {
	@Test
	void encryptionRoundTripUsesAuthenticatedCiphertext() {
		AesGcmSecretEncryptionProvider provider = new AesGcmSecretEncryptionProvider(
				Base64.getEncoder().encodeToString(new byte[32]));

		String ciphertext = provider.encrypt("secret-api-key");

		assertThat(ciphertext).startsWith("v1:").doesNotContain("secret-api-key");
		assertThat(provider.decrypt(ciphertext)).isEqualTo("secret-api-key");
		String tampered = ciphertext.substring(0, 4)
				+ (ciphertext.charAt(4) == 'A' ? 'B' : 'A')
				+ ciphertext.substring(5);
		assertThatThrownBy(() -> provider.decrypt(tampered))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void missingEncryptionKeyFailsClosed() {
		assertThatThrownBy(() -> new AesGcmSecretEncryptionProvider(""))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("必须配置");
	}

	@Test
	void privateAndNonHttpsEndpointsAreRejected() {
		assertThatThrownBy(() -> AiEndpointSecurity.validate("http://127.0.0.1:8080"))
				.isInstanceOf(AiModelConfigurationException.class);
		assertThatThrownBy(() -> AiEndpointSecurity.validate("https://user:pass@example.com"))
				.isInstanceOf(AiModelConfigurationException.class);
	}
}
