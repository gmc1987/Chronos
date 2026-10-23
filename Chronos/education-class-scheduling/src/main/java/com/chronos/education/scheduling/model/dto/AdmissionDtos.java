package com.chronos.education.scheduling.model.dto;

public final class AdmissionDtos {
	private AdmissionDtos() {}

	public record ReviewCommand(boolean passed, String remark) {}
	public record EnrollmentCommand(String administrativeClassId) {}
	public record RegistrationCommand(String status, String nationalStudentNo, String errorMessage) {}
	public record ImportRow(
			String sourceBatchNo,
			String candidateNo,
			String admissionNo,
			String studentName,
			String gender,
			Integer admissionYear,
			String educationLevel,
			String admittedMajorId,
			String plannedClassId,
			String phone) {}
	public record ImportResult(
			int totalCount,
			int successCount,
			java.util.List<String> errors,
			java.util.List<ImportRow> rows) {}
	public record BatchActionCommand(Integer admissionYear, Integer batchLimit,
			Integer targetClassSize, boolean dryRun) {}
	public record OfferingGenerationCommand(String semesterCode, Integer batchLimit, boolean dryRun) {}
}
