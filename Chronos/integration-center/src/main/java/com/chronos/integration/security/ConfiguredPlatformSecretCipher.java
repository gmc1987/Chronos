package com.chronos.integration.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import com.chronos.security.SecretEncryptionProvider;

/**
 * Bridges integration credentials to the platform encryption provider. The
 * provider owns key validation, authenticated encryption, and key management.
 */
@Component
@ConditionalOnBean(SecretEncryptionProvider.class)
public final class ConfiguredPlatformSecretCipher implements PlatformSecretCipher {
	private final SecretEncryptionProvider delegate;

	public ConfiguredPlatformSecretCipher(SecretEncryptionProvider delegate) {
		this.delegate = delegate;
	}

	@Override
	public String encrypt(String plaintext) {
		return delegate.encrypt(plaintext);
	}

	@Override
	public String decrypt(String ciphertext) {
		return delegate.decrypt(ciphertext);
	}
}
