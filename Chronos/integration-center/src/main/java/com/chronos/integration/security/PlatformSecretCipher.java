package com.chronos.integration.security;

/** Platform-owned secret encryption boundary. Implement with the deployment KMS before enabling credentials. */
public interface PlatformSecretCipher {
    String encrypt(String plaintext);
    String decrypt(String ciphertext);
}
