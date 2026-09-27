package com.chronos.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class MfaSecretCipher {
	private static final String CIPHER = "AES/GCM/NoPadding";
	private static final int IV_LENGTH = 12;
	private final SecretKeySpec key;
	private final SecureRandom random = new SecureRandom();

	public MfaSecretCipher(Environment environment) {
		String configured = environment.getProperty("security.mfa.encryption-key",
				environment.getProperty("security.jwt.secret", ""));
		if (configured == null || configured.isBlank()) {
			throw new IllegalStateException("security.mfa.encryption-key must be configured");
		}
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(configured.getBytes(StandardCharsets.UTF_8));
			this.key = new SecretKeySpec(digest, "AES");
		} catch (Exception exception) {
			throw new IllegalStateException("unable to initialize MFA secret cipher", exception);
		}
	}

	public String encrypt(String plaintext) {
		try {
			byte[] iv = new byte[IV_LENGTH];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(CIPHER);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
			byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding()
					.encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
		} catch (Exception exception) {
			throw new IllegalStateException("unable to encrypt MFA secret", exception);
		}
	}

	public String decrypt(String encoded) {
		try {
			byte[] packed = Base64.getUrlDecoder().decode(encoded);
			byte[] iv = java.util.Arrays.copyOfRange(packed, 0, IV_LENGTH);
			byte[] ciphertext = java.util.Arrays.copyOfRange(packed, IV_LENGTH, packed.length);
			Cipher cipher = Cipher.getInstance(CIPHER);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
			return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
		} catch (Exception exception) {
			throw new IllegalArgumentException("invalid MFA secret", exception);
		}
	}
}
