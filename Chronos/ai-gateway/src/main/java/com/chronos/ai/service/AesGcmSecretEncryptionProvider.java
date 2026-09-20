package com.chronos.ai.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.chronos.security.SecretEncryptionProvider;

/**
 * Authenticated envelope encryption provider. The key is supplied by the
 * deployment secret manager; there is deliberately no development fallback.
 */
@Service
public class AesGcmSecretEncryptionProvider implements SecretEncryptionProvider {
	private static final String PREFIX = "v1:";
	private static final int IV_LENGTH = 12;
	private static final int TAG_BITS = 128;

	private final SecretKeySpec key;
	private final SecureRandom random = new SecureRandom();

	public AesGcmSecretEncryptionProvider(
			@Value("${chronos.security.encryption-key:}") String encodedKey) {
		if (encodedKey == null || encodedKey.isBlank()) {
			throw new IllegalStateException("chronos.security.encryption-key 必须配置，禁止使用不安全回退");
		}
		try {
			byte[] decoded = Base64.getDecoder().decode(encodedKey);
			if (decoded.length != 16 && decoded.length != 24 && decoded.length != 32) {
				throw new IllegalArgumentException("AES key length");
			}
			this.key = new SecretKeySpec(decoded, "AES");
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("chronos.security.encryption-key 必须是有效的 Base64 AES 密钥", exception);
		}
	}

	@Override
	public String encrypt(String plaintext) {
		if (plaintext == null || plaintext.isBlank()) {
			throw new IllegalArgumentException("待加密值不能为空");
		}
		try {
			byte[] iv = new byte[IV_LENGTH];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
					.put(iv).put(encrypted).array());
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("敏感值加密失败", exception);
		}
	}

	@Override
	public String decrypt(String ciphertext) {
		if (ciphertext == null || !ciphertext.startsWith(PREFIX)) {
			throw new IllegalStateException("敏感值不是受支持的密文格式");
		}
		try {
			byte[] payload = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
			if (payload.length <= IV_LENGTH) throw new IllegalArgumentException("ciphertext");
			ByteBuffer buffer = ByteBuffer.wrap(payload);
			byte[] iv = new byte[IV_LENGTH];
			buffer.get(iv);
			byte[] encrypted = new byte[buffer.remaining()];
			buffer.get(encrypted);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException exception) {
			throw new IllegalStateException("敏感值解密失败", exception);
		}
	}

	@Override
	public String keyVersion() {
		return "v1";
	}

	@Override
	public String fingerprint(String plaintext) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(plaintext.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("敏感值指纹计算失败", exception);
		}
	}
}
