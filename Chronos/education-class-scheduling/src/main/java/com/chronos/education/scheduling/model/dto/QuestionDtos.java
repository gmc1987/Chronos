package com.chronos.education.scheduling.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class QuestionDtos {
	private QuestionDtos() {}
	public record BankRequest(String offeringId, String courseId, String name, String description,
			String visibility) {}
	public record OptionRequest(String key, String text, Integer sortOrder, Boolean correct) {}
	public record QuestionRequest(String bankId, String questionType, String difficulty, String stem,
			BigDecimal score, String answer, String analysis, String answerSchemaJson,
			LocalDateTime usableFrom, LocalDateTime usableUntil, List<OptionRequest> options,
			List<String> knowledgePointIds, List<String> fileIds) {}
	public record KnowledgePointRequest(String parentId, String subjectId, String courseId,
			String code, String name, String description, String learningObjective,
			Short level, Integer sortOrder) {}
	public record RowError(int row, String field, String message) {}
	public record ImportPreview(int acceptedRows, List<RowError> errors) {}
}
