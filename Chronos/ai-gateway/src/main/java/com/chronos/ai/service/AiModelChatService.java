package com.chronos.ai.service;

/**
 * Public AI gateway used by other modules. Callers provide a persisted model
 * id when they need an explicit model, or {@code null} to use the configured
 * default and then the legacy Spring AI fallback.
 */
public interface AiModelChatService {
	String chat(String modelId, String message);

	/** Evicts a model after an administrative configuration change. */
	default void invalidate(String modelId) {
		// Implementations that cache provider clients override this hook.
	}
}
