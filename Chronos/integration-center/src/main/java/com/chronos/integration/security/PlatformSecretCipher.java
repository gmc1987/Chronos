package com.chronos.integration.security;

/** Integration-center view of the platform-owned secret encryption boundary. */
public interface PlatformSecretCipher {
    String encrypt(String plaintext);
    String decrypt(String ciphertext);
}
