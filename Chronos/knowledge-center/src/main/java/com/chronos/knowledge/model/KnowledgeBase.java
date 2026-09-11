package com.chronos.knowledge.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 一组具有相同使用范围和维护责任的知识文档。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "kb_knowledge_base")
public class KnowledgeBase extends BaseEntity {
	@Column(name = "base_code", length = 64, nullable = false, unique = true)
	private String baseCode;

	@Column(name = "base_name", length = 128, nullable = false)
	private String baseName;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@Column(name = "organization_id", length = 64)
	private String organizationId;

	@Column(name = "enabled", nullable = false)
	private Boolean enabled = true;

	/**
	 * Cross-module reference only; model validation and invocation live in
	 * ai-gateway. Null means use the gateway's default model.
	 */
	@Column(name = "answer_model_id", length = 64)
	private String answerModelId;
}
