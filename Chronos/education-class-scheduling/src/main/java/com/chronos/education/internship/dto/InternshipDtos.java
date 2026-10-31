package com.chronos.education.internship.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class InternshipDtos {
	private InternshipDtos() {
	}

	public record CompanyCommand(
			String schoolId, String companyCode, String companyName, String creditCode,
			String contactName, String contactPhone, String address, String riskLevel) {
	}
	public record DecisionCommand(boolean approved, String comment) {
	}
	public record PositionCommand(
			String schoolId, String companyId, String positionName, Integer capacity,
			String mentorName, String mentorPhone, String safetyRequirements) {
	}
	public record PlacementCommand(
			String schoolId, String termCode, String studentId, String companyId,
			String positionId, LocalDate startDate, LocalDate endDate,
			String agreementFileId, String consentFileId, String insuranceFileId) {
	}
	public record ActivityCommand(
			String activityType, LocalDate activityDate, String content, String result, String riskLevel) {
	}
	public record AssessmentCommand(
			BigDecimal companyScore, BigDecimal schoolScore, BigDecimal finalScore, String comment) {
	}
	public record IncidentCommand(
			LocalDateTime occurredAt, String severity, String description, String emergencyAction) {
	}
	public record ClosureCommand(String comment) {
	}
}
