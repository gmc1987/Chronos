package com.chronos.ai.service;

/** Explicit failure for callers that require a validated structured response. */
public class AiStructuredOutputException extends RuntimeException {
	public AiStructuredOutputException(String message) {
		super(message);
	}

	public AiStructuredOutputException(String message, Throwable cause) {
		super(message, cause);
	}
}
