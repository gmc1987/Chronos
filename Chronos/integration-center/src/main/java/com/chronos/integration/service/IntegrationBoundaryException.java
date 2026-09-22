package com.chronos.integration.service;

public final class IntegrationBoundaryException extends IllegalArgumentException {
	private final String code;

	public IntegrationBoundaryException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
