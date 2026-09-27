package com.chronos.ai.service;

/**
 * Public AI gateway used by other modules. Callers provide a persisted model
 * id when they need an explicit model, or {@code null} to use the configured
 * default and then the legacy Spring AI fallback.
 */
public interface AiModelChatService {
	String chat(String modelId, String message);

	/**
	 * Optional structured-output contract. Implementations must validate the
	 * response against the requested schema before returning it. The legacy
	 * plain-text API remains unchanged for existing callers.
	 */
	default String chatStructured(String modelId, String schemaId, String message) {
		throw new AiStructuredOutputException("当前 AI 模型未提供结构化输出能力");
	}

	/** Evicts a model after an administrative configuration change. */
	default void invalidate(String modelId) {
		// Implementations that cache provider clients override this hook.
	}
}
