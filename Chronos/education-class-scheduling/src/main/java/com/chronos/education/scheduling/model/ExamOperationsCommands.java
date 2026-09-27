package com.chronos.education.scheduling.model;

public final class ExamOperationsCommands {
	private ExamOperationsCommands() {
	}

	public record Registration(String sessionId, String studentId) {
	}

	public record Accommodation(
			String registrationId,
			String typeCode,
			Integer extraMinutes,
			String roomRequirementJson,
			String fileId) {
	}

	public record AccommodationDecision(boolean approve, String reason) {
	}

	public record Material(
			String sessionId,
			String materialType,
			String batchNo,
			Integer plannedQuantity,
			String sealNo) {
	}

	public record Handover(
			String ledgerId,
			String receivedBy,
			Integer quantity,
			String differenceReason) {
	}

	public record Incident(
			String sessionId,
			String roomId,
			String candidateId,
			String incidentType,
			String severity,
			String description) {
	}

	public record IncidentAction(String actionType, String conclusion, String fileId) {
	}
}
