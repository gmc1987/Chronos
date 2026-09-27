package com.chronos.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class TotpVerifier {
	private TotpVerifier() {}

	public static boolean verify(String base32Secret, String code, Instant now) {
		if (code == null || !code.matches("\\d{6}")) {
			return false;
		}
		byte[] secret = decodeBase32(base32Secret);
		long counter = now.getEpochSecond() / 30;
		for (long offset = -1; offset <= 1; offset++) {
			if (generate(secret, counter + offset).equals(code)) {
				return true;
			}
		}
		return false;
	}

	private static String generate(byte[] secret, long counter) {
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(secret, "HmacSHA1"));
			byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
			int offset = hash[hash.length - 1] & 0x0f;
			int binary = ((hash[offset] & 0x7f) << 24)
					| ((hash[offset + 1] & 0xff) << 16)
					| ((hash[offset + 2] & 0xff) << 8)
					| (hash[offset + 3] & 0xff);
			return String.format("%06d", binary % 1_000_000);
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("unable to verify TOTP", exception);
		}
	}

	private static byte[] decodeBase32(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("TOTP secret is required");
		}
		String normalized = value.replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
		StringBuilder bits = new StringBuilder();
		for (char character : normalized.toCharArray()) {
			int digit = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(character);
			if (digit < 0) {
				throw new IllegalArgumentException("invalid TOTP secret");
			}
			bits.append(String.format("%5s", Integer.toBinaryString(digit)).replace(' ', '0'));
		}
		byte[] result = new byte[bits.length() / 8];
		for (int i = 0; i < result.length; i++) {
			result[i] = (byte) Integer.parseInt(bits.substring(i * 8, i * 8 + 8), 2);
		}
		return result;
	}
}
