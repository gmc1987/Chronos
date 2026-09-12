package com.chronos.education.scheduling.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeachingCenterResourceCommand(
		@NotBlank @Size(max = 32) String resourceType,
		String offeringId,
		@Size(max = 64) String scheduleEntryId,
		@NotBlank @Size(max = 200) String title,
		@Size(max = 64) String category,
		@Size(max = 24) String status,
		String content,
		@Size(max = 64) String fileId,
		String metadataJson) {
}
