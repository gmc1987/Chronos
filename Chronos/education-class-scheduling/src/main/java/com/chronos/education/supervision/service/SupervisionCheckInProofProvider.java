package com.chronos.education.supervision.service;

import com.chronos.education.supervision.model.SupervisionAssignment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Trusted adapter for campus location or QR verification.
 *
 * <p>No implementation is registered until a deployment provides a trusted
 * verifier. The service consequently fails closed instead of accepting a
 * client-provided location or QR value as proof.</p>
 */
public interface SupervisionCheckInProofProvider {
	VerificationResult verify(VerificationRequest request);

	record VerificationRequest(
			SupervisionAssignment assignment,
			Proof proof,
			Instant verifiedAt) {
	}

	/**
	 * The token is opaque to Chronos and must be signed/verified by the provider.
	 * It must not contain client-supplied time or location claims that Chronos
	 * trusts without provider verification.
	 */
	record Proof(@NotBlank @Size(max = 64) String providerId,
			@NotBlank @Size(max = 4096) String signedProof) {
	}

	enum VerificationCode {
		VERIFIED,
		CONFIGURATION_MISSING,
		PROVIDER_UNAVAILABLE,
		INVALID_PROOF,
		PROOF_EXPIRED,
		SCOPE_MISMATCH,
		REPLAY_DETECTED
	}

	record VerificationResult(
			VerificationCode code,
			String proofId,
			Instant expiresAt) {
		public boolean verified() {
			return code == VerificationCode.VERIFIED;
		}
	}
}
