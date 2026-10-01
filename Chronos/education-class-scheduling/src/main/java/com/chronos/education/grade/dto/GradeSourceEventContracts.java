package com.chronos.education.grade.dto;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Stable payloads emitted by the persisted grade, exam and homework boundaries. */
public final class GradeSourceEventContracts {
	private GradeSourceEventContracts() {
	}

	public record CourseGradesPublishedV1(String eventId, String eventType,
			OffsetDateTime occurredAt, int payloadVersion, String gradebookId,
			String offeringId, int versionNo, String snapshotHash, String publishedBy) {
	}

	public record ExamScoresConfirmedV1(String eventId, String eventType,
			OffsetDateTime occurredAt, int payloadVersion, String examPlanId,
			String sessionId, String offeringId, String studentId, BigDecimal rawScore,
			BigDecimal maxScore, String specialStatus, OffsetDateTime confirmedAt) {
	}

	public record HomeworkGradesPublishedV1(String eventId, String eventType,
			OffsetDateTime occurredAt, int payloadVersion, String assignmentId,
			String offeringId, String studentId, BigDecimal score, BigDecimal maxScore,
			OffsetDateTime publishedAt) {
	}
}
