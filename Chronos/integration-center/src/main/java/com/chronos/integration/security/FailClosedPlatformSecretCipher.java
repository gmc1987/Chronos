package com.chronos.integration.security;

import org.springframework.stereotype.Component;

/** Deliberately refuses to persist or reveal secrets until a real platform cipher is configured. */
@Component
public final class FailClosedPlatformSecretCipher implements PlatformSecretCipher {
    private static final String MESSAGE = "Platform secret cipher is not configured; refusing secret operation";
    @Override public String encrypt(String plaintext) { throw new IllegalStateException(MESSAGE); }
    @Override public String decrypt(String ciphertext) { throw new IllegalStateException(MESSAGE); }
}
