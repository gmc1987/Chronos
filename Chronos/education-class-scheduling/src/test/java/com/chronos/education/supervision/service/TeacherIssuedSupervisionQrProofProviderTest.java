package com.chronos.education.supervision.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chronos.education.supervision.model.SupervisionAssignment;
import com.chronos.education.supervision.service.SupervisionCheckInProofProvider.VerificationCode;
import com.chronos.education.supervision.service.SupervisionCheckInProofProvider.VerificationRequest;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TeacherIssuedSupervisionQrProofProviderTest {
	private final TeacherIssuedSupervisionQrProofProvider provider =
			new TeacherIssuedSupervisionQrProofProvider("test-only-secret-at-least-32-characters-long");

	@Test
	void verifiesOnlyMatchingUnexpiredSignedAssignment() {
		Instant issuedAt = Instant.parse("2026-10-15T12:00:00Z");
		SupervisionAssignment assignment = assignment("task-1", "teacher-1");
		var proof = provider.issue(assignment, issuedAt);
		var valid = provider.verify(new VerificationRequest(assignment, proof, issuedAt.plusSeconds(299)));
		assertThat(valid.code()).isEqualTo(VerificationCode.VERIFIED);
		assertThat(valid.proofId()).startsWith("task-1:");
		assertThat(provider.verify(new VerificationRequest(assignment, proof, issuedAt.plusSeconds(300))).code())
				.isEqualTo(VerificationCode.PROOF_EXPIRED);
		assertThat(provider.verify(new VerificationRequest(assignment("task-2", "teacher-1"),
				proof, issuedAt.plusSeconds(1))).code()).isEqualTo(VerificationCode.SCOPE_MISMATCH);
		assertThat(provider.verify(new VerificationRequest(assignment("task-1", "teacher-2"),
				proof, issuedAt.plusSeconds(1))).code()).isEqualTo(VerificationCode.SCOPE_MISMATCH);
	}

	@Test
	void rejectsTamperedSignature() {
		Instant now = Instant.parse("2026-10-15T12:00:00Z");
		SupervisionAssignment assignment = assignment("task-1", "teacher-1");
		var proof = provider.issue(assignment, now);
		int separator = proof.signedProof().indexOf('.');
		var altered = new SupervisionCheckInProofProvider.Proof(proof.providerId(),
				proof.signedProof().substring(0, separator + 1) + "X"
						+ proof.signedProof().substring(separator + 2));
		assertThat(provider.verify(new VerificationRequest(assignment, altered, now)).code())
				.isEqualTo(VerificationCode.INVALID_PROOF);
	}

	private SupervisionAssignment assignment(String id, String teacherId) {
		SupervisionAssignment value = new SupervisionAssignment();
		value.setId(id);
		value.setTeacherId(teacherId);
		return value;
	}
}
