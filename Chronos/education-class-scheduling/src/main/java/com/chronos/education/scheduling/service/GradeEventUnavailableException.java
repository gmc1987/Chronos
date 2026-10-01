package com.chronos.education.scheduling.service;

/** Machine-readable boundary for source events that cannot be safely projected. */
public class GradeEventUnavailableException extends IllegalArgumentException {
	private final String code;

	public GradeEventUnavailableException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String code() {
		return code;
	}
}
