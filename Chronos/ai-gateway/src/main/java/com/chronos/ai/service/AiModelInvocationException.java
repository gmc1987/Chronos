package com.chronos.ai.service;

/** A model invocation failed after a valid model configuration was selected. */
public class AiModelInvocationException extends IllegalStateException {
	public AiModelInvocationException(String message, Throwable cause) {
		super(message, cause);
	}
}
