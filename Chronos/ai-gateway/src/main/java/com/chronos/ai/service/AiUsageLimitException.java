package com.chronos.ai.service;

/** A visible business rejection caused by an AI usage policy. */
public class AiUsageLimitException extends IllegalStateException {
	private static final long serialVersionUID = 1L;

	public AiUsageLimitException(String message) {
		super(message);
	}
}
