package com.chronos.education.grade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class GradeDtos {
	private GradeDtos() {
	}

	public record SchemeCommand(String offeringId, String name, BigDecimal totalScore, BigDecimal passScore,
			List<ComponentCommand> components, Long rowVersion) {
	}

	public record ComponentCommand(String code, String name, String sourceType, BigDecimal weight,
			BigDecimal maxScore, Integer sortOrder) {
	}

	public record GradebookCommand(String offeringId, String schemeId) {
	}

	public record GradeItemCommand(String componentId, String studentId, BigDecimal rawScore, String specialStatus,
			String remark) {
	}

	public record ItemsCommand(List<GradeItemCommand> items, Long rowVersion) {
	}

	public record RejectCommand(String comment) {
	}

	public record GradebookDetailResponse(String id, String offeringId, String schemeId, String status,
			Integer submissionNo, Long rowVersion, LocalDateTime submittedAt, LocalDateTime publishedAt,
			List<StudentSnapshot> students, List<ComponentDetail> components, List<GradeItemDetail> items,
			List<SnapshotMetadata> snapshots) {
	}

	public record StudentSnapshot(String id, String studentId, String studentNo, String studentName,
			String administrativeClassId, String enrollmentStatus, String sourceMemberId, LocalDateTime enrolledAt,
			LocalDateTime withdrawnAt, Integer snapshotVersion, String snapshotHash) {
	}

	public record ComponentDetail(String id, String code, String name, String sourceType, BigDecimal weight,
			BigDecimal maxScore, Integer sortOrder) {
	}

	public record GradeItemDetail(String id, String componentId, String studentId, BigDecimal rawScore,
			BigDecimal convertedScore, String specialStatus, String remark, Long rowVersion) {
	}

	public record SnapshotMetadata(Integer versionNo, String snapshotHash, String publishedBy,
			LocalDateTime publishedAt) {
	}
}
