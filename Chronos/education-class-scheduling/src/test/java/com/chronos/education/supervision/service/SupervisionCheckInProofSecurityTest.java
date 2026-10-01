package com.chronos.education.supervision.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.chronos.education.grade.service.DomainEventOutboxService;
import com.chronos.education.supervision.dao.*;
import com.chronos.education.supervision.model.SupervisionAssignment;
import com.chronos.service.iService.IAuditLogService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SupervisionCheckInProofSecurityTest {
	@Test
	void rejectsExpiredProviderResultAndRecordsMachineReadableAuditCode() {
		SupervisionAssignment assignment = assignment();
		SupervisionCheckInProofProvider provider = mock(SupervisionCheckInProofProvider.class);
		when(provider.verify(any())).thenReturn(new SupervisionCheckInProofProvider.VerificationResult(
				SupervisionCheckInProofProvider.VerificationCode.PROOF_EXPIRED,
				"proof-1", Instant.now().minusSeconds(1)));
		IAuditLogService audit = mock(IAuditLogService.class);
		SupervisionAssignmentRepository assignments = assignments(assignment);
		SupervisionCenterService service = service(assignments, provider, audit);

		assertThatThrownBy(() -> service.checkIn("assignment-1", "supervisor-1",
				new SupervisionCheckInProofProvider.Proof("qr", "signed-token")))
				.hasMessage("SUPERVISION_CHECK_IN_PROOF_EXPIRED");
		verify(audit).log("supervisor-1", "EDU_SUPERVISION_CHECK_IN_PROOF",
				"assignmentId=assignment-1,code=PROOF_EXPIRED");
		verify(assignments, never()).save(any());
	}

	@Test
	void consumesVerifiedProofOnlyOnce() {
		SupervisionAssignment assignment = assignment();
		SupervisionCheckInProofProvider provider = mock(SupervisionCheckInProofProvider.class);
		when(provider.verify(any())).thenReturn(new SupervisionCheckInProofProvider.VerificationResult(
				SupervisionCheckInProofProvider.VerificationCode.VERIFIED,
				"proof-1", Instant.now().plusSeconds(60)));
		SupervisionAssignmentRepository assignments = mock(SupervisionAssignmentRepository.class);
		when(assignments.findByIdAndSupervisorId("assignment-1", "supervisor-1"))
				.thenReturn(Optional.of(assignment));
		SupervisionCenterService service = service(assignments, provider, mock(IAuditLogService.class));
		SupervisionCheckInProofProvider.Proof proof =
				new SupervisionCheckInProofProvider.Proof("qr", "signed-token");

		service.checkIn("assignment-1", "supervisor-1", proof);
		assignment.setStatus("ACCEPTED");
		assertThatThrownBy(() -> service.checkIn("assignment-1", "supervisor-1", proof))
				.hasMessage("SUPERVISION_CHECK_IN_REPLAY_DETECTED");
		verify(assignments).save(assignment);
	}

	private SupervisionAssignment assignment() {
		SupervisionAssignment assignment = new SupervisionAssignment();
		assignment.setId("assignment-1");
		assignment.setSupervisorId("supervisor-1");
		assignment.setStatus("ACCEPTED");
		return assignment;
	}

	private SupervisionCenterService service(
			SupervisionAssignment assignment,
			SupervisionCheckInProofProvider provider,
			IAuditLogService audit) {
		SupervisionAssignmentRepository assignments = assignments(assignment);
		return service(assignments, provider, audit);
	}

	private SupervisionAssignmentRepository assignments(SupervisionAssignment assignment) {
		SupervisionAssignmentRepository assignments = mock(SupervisionAssignmentRepository.class);
		when(assignments.findByIdAndSupervisorId("assignment-1", "supervisor-1"))
				.thenReturn(Optional.of(assignment));
		return assignments;
	}

	private SupervisionCenterService service(
			SupervisionAssignmentRepository assignments,
			SupervisionCheckInProofProvider provider,
			IAuditLogService audit) {
		return new SupervisionCenterService(
				mock(SupervisionPlanRepository.class), assignments,
				mock(SupervisionRecordRepository.class), mock(SupervisionIssueRepository.class),
				mock(SupervisionRectificationRepository.class), mock(SupervisionReviewRepository.class),
				null, mock(DomainEventOutboxService.class), audit, null, null, provider);
	}
}
