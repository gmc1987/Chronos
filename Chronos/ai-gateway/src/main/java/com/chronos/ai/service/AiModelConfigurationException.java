package com.chronos.ai.service;

/**
 * A client-visible configuration error raised by the AI gateway.
 *
 * <p>It extends the existing project validation exception type so the
 * platform's standard exception handler returns a structured 400 response.</p>
 */
public class AiModelConfigurationException extends IllegalArgumentException {
	public AiModelConfigurationException(String message) {
		super(message);
	}
}
