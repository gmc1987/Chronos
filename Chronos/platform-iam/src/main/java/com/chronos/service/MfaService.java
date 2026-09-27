package com.chronos.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IMfaFactorRepository;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.MfaFactor;
import com.chronos.security.MfaSecretCipher;
import com.chronos.security.TotpVerifier;
import com.chronos.service.iService.IAuditLogService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MfaService {
	private final IMfaFactorRepository factors;
	private final IAdminUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final MfaSecretCipher secretCipher;
	private final IAuditLogService audit;
	private final SecureRandom random = new SecureRandom();

	@Transactional
	public Enrollment enroll(String username, String totpSecret, String actor) {
		AdminUser user = requireUser(username);
		if (totpSecret == null || totpSecret.isBlank()) {
			throw new IllegalArgumentException("TOTP secret is required");
		}
		MfaFactor factor = factors.findByUserIdAndFactorType(user.getId(), "TOTP")
				.orElseGet(MfaFactor::new);
		factor.setUserId(user.getId());
		factor.setFactorType("TOTP");
		factor.setSecretCiphertext(secretCipher.encrypt(totpSecret));
		List<String> recoveryCodes = generateRecoveryCodes();
		factor.setRecoveryCodeHashes(String.join("|", recoveryCodes.stream()
				.map(passwordEncoder::encode).toList()));
		factor.setStatus("PENDING");
		factor.setEnrolledAt(LocalDateTime.now());
		MfaFactor saved = factors.save(factor);
		audit.log(actor, "IAM_MFA_ENROLL", user.getId());
		return new Enrollment(saved.getId(), recoveryCodes);
	}

	@Transactional
	public boolean verifyTotp(String username, String code, String actor) {
		AdminUser user = requireUser(username);
		MfaFactor factor = factors.findByUserIdAndFactorType(user.getId(), "TOTP")
				.orElseThrow(() -> new IllegalStateException("MFA is not enrolled"));
		Instant now = Instant.now();
		var matchingCounter = TotpVerifier.matchingCounter(
				secretCipher.decrypt(factor.getSecretCiphertext()), code, now);
		if (matchingCounter.isEmpty() || factors.claimTimeStep(factor.getId(), matchingCounter.getAsLong()) != 1) {
			return false;
		}
		factor.setStatus("ENROLLED");
		factor.setVerifiedAt(LocalDateTime.now());
		factors.save(factor);
		audit.log(actor, "IAM_MFA_VERIFY", user.getId());
		return true;
	}

	@Transactional
	public boolean verifyRecoveryCode(String username, String code, String actor) {
		AdminUser user = requireUser(username);
		MfaFactor factor = factors.findByUserIdAndFactorType(user.getId(), "TOTP")
				.orElseThrow(() -> new IllegalStateException("MFA is not enrolled"));
		List<String> hashes = splitHashes(factor.getRecoveryCodeHashes());
		for (int i = 0; i < hashes.size(); i++) {
			if (passwordEncoder.matches(code, hashes.get(i))) {
				hashes.remove(i);
				factor.setRecoveryCodeHashes(String.join("|", hashes));
				factor.setStatus("ENROLLED");
				factor.setVerifiedAt(LocalDateTime.now());
				factors.save(factor);
				audit.log(actor, "IAM_MFA_RECOVERY_CODE_USE", user.getId());
				return true;
			}
		}
		return false;
	}

	private AdminUser requireUser(String username) {
		AdminUser user = users.findByUsername(username);
		if (user == null) {
			throw new IllegalArgumentException("user not found");
		}
		return user;
	}

	private List<String> generateRecoveryCodes() {
		List<String> codes = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			codes.add("%08d".formatted(random.nextInt(100_000_000)));
		}
		return codes;
	}

	private List<String> splitHashes(String hashes) {
		return hashes == null || hashes.isBlank() ? new ArrayList<>()
				: new ArrayList<>(List.of(hashes.split("\\|")));
	}

	public record Enrollment(String factorId, List<String> recoveryCodes) {}
}
