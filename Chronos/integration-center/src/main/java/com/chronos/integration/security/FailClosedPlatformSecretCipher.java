package com.chronos.integration.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import com.chronos.security.SecretEncryptionProvider;

/** Deliberately refuses to persist or reveal secrets until a real platform cipher is configured. */
@Component
@ConditionalOnMissingBean(SecretEncryptionProvider.class)
public final class FailClosedPlatformSecretCipher implements PlatformSecretCipher {
    private static final String MESSAGE = "Platform secret cipher is not configured; refusing secret operation";
    @Override public String encrypt(String plaintext) { throw new IllegalStateException(MESSAGE); }
    @Override public String decrypt(String ciphertext) { throw new IllegalStateException(MESSAGE); }
}
