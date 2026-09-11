package com.chronos.knowledge.service;

import java.util.List;

/** Small adapter boundary; implementations isolate collections by knowledge base. */
public interface MilvusVectorStore {
	void upsert(String knowledgeBaseId, String chunkId, float[] vector);
	List<String> search(String knowledgeBaseId, float[] vector, int limit);
	void delete(String knowledgeBaseId, String chunkId);
	boolean available();
}
