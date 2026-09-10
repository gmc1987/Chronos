package com.chronos.file.model;

public record ManagedFileView(
		String id,
		String name,
		String contentType,
		Long size,
		String sha256,
		String businessType,
		String businessId,
		String downloadUrl) {
	public static ManagedFileView from(ManagedFile value) {
		return new ManagedFileView(
				value.getId(),
				value.getOriginalName(),
				value.getContentType(),
				value.getFileSize(),
				value.getSha256(),
				value.getBusinessType(),
				value.getBusinessId(),
				"/files/" + value.getId() + "/content");
	}
}
