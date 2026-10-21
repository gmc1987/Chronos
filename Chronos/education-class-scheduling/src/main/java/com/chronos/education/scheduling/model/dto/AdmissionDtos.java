package com.chronos.education.scheduling.model.dto;

public final class AdmissionDtos {
	private AdmissionDtos() {}

	public record ReviewCommand(boolean passed, String remark) {}
	public record EnrollmentCommand(String administrativeClassId) {}
	public record RegistrationCommand(String status, String nationalStudentNo, String errorMessage) {}
	public record ImportResult(int totalCount, int successCount, java.util.List<String> errors) {}
}
