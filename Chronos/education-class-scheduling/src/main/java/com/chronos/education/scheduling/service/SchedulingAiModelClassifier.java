package com.chronos.education.scheduling.service;

import com.chronos.ai.service.AiModelChatService;
import com.chronos.ai.service.AiStructuredOutputException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Model classification is advisory: every clause must survive verbatim and pass domain parsing. */
@Service
public class SchedulingAiModelClassifier {
	private final AiModelChatService models;
	private final ObjectMapper json;

	public SchedulingAiModelClassifier(AiModelChatService models, ObjectMapper json) {
		this.models = models;
		this.json = json;
	}

	public List<Clause> classify(String input) {
		List<String> original = input.lines()
				.flatMap(line -> java.util.Arrays.stream(line.split("[；;。，,]+")))
				.map(String::strip)
				.filter(value -> !value.isEmpty())
				.toList();
		if (original.isEmpty() || original.size() > 30) {
			throw new AiStructuredOutputException("需求子句数量无效");
		}
		String response = models.chatStructured(null, "schedule.requirement.clauses.v1", input);
		try {
			JsonNode root = json.readTree(response);
			if (root == null || !root.isObject() || root.size() != 1
					|| !root.has("clauses") || !root.path("clauses").isArray()
					|| root.path("clauses").size() != original.size()) {
				throw new AiStructuredOutputException("模型遗漏或新增需求子句");
			}
			List<Clause> result = new java.util.ArrayList<>();
			for (int index = 0; index < original.size(); index++) {
				JsonNode item = root.path("clauses").get(index);
				if (!item.isObject() || item.size() != 2
						|| !item.path("text").isTextual()
						|| !original.get(index).equals(item.path("text").asText().strip())
						|| !item.path("classification").isTextual()
						|| !Set.of("TEACHER_SLOT", "GENERATION", "UNSUPPORTED")
								.contains(item.path("classification").asText())) {
					throw new AiStructuredOutputException("模型需求分类与用户原文不一致");
				}
				result.add(new Clause(original.get(index), item.path("classification").asText()));
			}
			return List.copyOf(result);
		} catch (JsonProcessingException exception) {
			throw new AiStructuredOutputException("模型结构化需求无法解析", exception);
		}
	}

	public record Clause(String text, String classification) {
	}
}
