package com.chronos.ai.service;

import java.util.List;

/** Provider-neutral embedding gateway contract used by knowledge-center. */
public interface EmbeddingService {
	List<float[]> embed(List<String> texts, String modelId);

	default float[] embed(String text, String modelId) {
		return embed(List.of(text), modelId).get(0);
	}
}
