package com.chronos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IMfaFactorRepository;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.MfaFactor;
import com.chronos.security.MfaSecretCipher;
import com.chronos.service.iService.IAuditLogService;

class MfaServiceReplayTest {
	@Test
	void acceptsAnOtpOnlyWhenTheRepositoryAtomicallyClaimsItsTimeStep() {
		IMfaFactorRepository factors = mock(IMfaFactorRepository.class);
		IAdminUserRepository users = mock(IAdminUserRepository.class);
		PasswordEncoder passwords = mock(PasswordEncoder.class);
		MfaSecretCipher cipher = mock(MfaSecretCipher.class);
		IAuditLogService audit = mock(IAuditLogService.class);
		MfaService service = new MfaService(factors, users, passwords, cipher, audit);

		AdminUser user = new AdminUser();
		user.setId("user-1");
		user.setUsername("alice");
		MfaFactor factor = new MfaFactor();
		factor.setId("factor-1");
		factor.setSecretCiphertext("encrypted");
		when(users.findByUsername("alice")).thenReturn(user);
		when(factors.findByUserIdAndFactorType("user-1", "TOTP"))
				.thenReturn(java.util.Optional.of(factor));
		when(cipher.decrypt("encrypted")).thenReturn("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
		when(factors.claimTimeStep(eq("factor-1"), anyLong())).thenReturn(1, 0);

		boolean firstAccepted = false;
		for (int attempt = 0; attempt < 3 && !firstAccepted; attempt++) {
			firstAccepted = service.verifyTotp("alice",
					currentCode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Instant.now()), "alice");
		}
		assertThat(firstAccepted).isTrue();
		assertThat(service.verifyTotp("alice",
				currentCode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Instant.now()), "alice")).isFalse();
	}

	private static String currentCode(String secret, Instant now) {
		long counter = now.getEpochSecond() / 30;
		byte[] decoded = decodeBase32(secret);
		try {
			var mac = javax.crypto.Mac.getInstance("HmacSHA1");
			mac.init(new javax.crypto.spec.SecretKeySpec(decoded, "HmacSHA1"));
			byte[] hash = mac.doFinal(java.nio.ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
			int offset = hash[hash.length - 1] & 0x0f;
			int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
					| ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
			return "%06d".formatted(binary % 1_000_000);
		} catch (Exception ex) {
			throw new AssertionError(ex);
		}
	}

	private static byte[] decodeBase32(String value) {
		String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
		StringBuilder bits = new StringBuilder();
		for (char character : value.toCharArray()) {
			bits.append("%5s".formatted(Integer.toBinaryString(alphabet.indexOf(character))).replace(' ', '0'));
		}
		byte[] result = new byte[bits.length() / 8];
		for (int i = 0; i < result.length; i++) {
			result[i] = (byte) Integer.parseInt(bits.substring(i * 8, i * 8 + 8), 2);
		}
		return result;
	}
}
