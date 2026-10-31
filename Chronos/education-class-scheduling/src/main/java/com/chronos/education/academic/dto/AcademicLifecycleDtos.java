package com.chronos.education.academic.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 教务生产主链的显式命令和只读视图。 */
public final class AcademicLifecycleDtos {
	private AcademicLifecycleDtos() {
	}

	public record ProgramCommand(
			String schoolId,
			String majorId,
			Integer cohortYear,
			String educationLevel,
			String programCode,
			String programName,
			BigDecimal minimumCredits,
			LocalDate effectiveStartDate,
			LocalDate effectiveEndDate) {
	}

	public record ProgramCourseCommand(
			String courseId,
			Integer suggestedTermNo,
			String courseNature,
			BigDecimal credits,
			List<String> prerequisiteCourseIds,
			String courseGroupCode,
			Integer sortOrder) {
	}

	public record ProgramView(
			String id,
			String schoolId,
			String majorId,
			Integer cohortYear,
			String educationLevel,
			String programCode,
			String programName,
			Integer versionNo,
			BigDecimal minimumCredits,
			Integer totalHours,
			String status,
			Long rowVersion,
			List<ProgramCourseView> courses) {
	}

	public record ProgramCourseView(
			String id,
			String courseId,
			String courseCode,
			String courseName,
			Integer suggestedTermNo,
			String courseNature,
			BigDecimal credits,
			Integer totalHours,
			Integer theoryHours,
			Integer practiceHours,
			List<String> prerequisiteCourseIds,
			String courseGroupCode,
			Integer sortOrder) {
	}

	public record RegistrationCommand(
			String schoolId,
			String termCode,
			String studentId,
			String programId) {
	}

	public record RegistrationDecisionCommand(boolean approved, String reason) {
	}

	public record EnrollmentCommand(
			String schoolId,
			String termCode,
			String studentId,
			String offeringId,
			String programCourseId,
			String source) {
	}

	public record WithdrawalCommand(String reason) {
	}

	public record GraduationAuditCommand(String schoolId, String studentId, String programId) {
	}

	public record GraduationDecisionCommand(boolean approved, String reason) {
	}

	public record ReadinessView(
			String studentId,
			String studentNo,
			String studentName,
			BigDecimal requiredCredits,
			BigDecimal earnedCredits,
			List<Map<String, Object>> unmetRequirements,
			boolean ready) {
	}
}
