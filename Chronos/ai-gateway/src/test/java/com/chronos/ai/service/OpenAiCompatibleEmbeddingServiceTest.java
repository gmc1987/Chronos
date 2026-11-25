package com.chronos.ai.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleEmbeddingServiceTest {
	@Test
	void acceptsProviderRootsAndVersionedBaseUrls() {
		assertEquals("https://provider.example/v1/embeddings",
				OpenAiCompatibleEmbeddingService.embeddingEndpoint("https://provider.example/"));
		assertEquals("https://provider.example/compatible-mode/v1/embeddings",
				OpenAiCompatibleEmbeddingService.embeddingEndpoint("https://provider.example/compatible-mode/v1/"));
		assertEquals("https://provider.example/v1/embeddings",
				OpenAiCompatibleEmbeddingService.embeddingEndpoint("https://provider.example/v1/embeddings/"));
	}

	@Test
	void preservesInputOrderAndRejectsWrongDimensions() throws Exception {
		var response = new ObjectMapper().readTree("""
				{"data":[{"index":1,"embedding":[3,4]},{"index":0,"embedding":[1,2]}]}
				""");
		var vectors = OpenAiCompatibleEmbeddingService.decodeVectors(response, 2, 2);
		assertArrayEquals(new float[] {1, 2}, vectors.get(0));
		assertArrayEquals(new float[] {3, 4}, vectors.get(1));
		assertThrows(IllegalStateException.class,
				() -> OpenAiCompatibleEmbeddingService.decodeVectors(response, 2, 3));
	}
}
