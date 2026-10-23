package com.chronos.education.operationagent.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_operation_agent_condition", uniqueConstraints = @UniqueConstraint(
		name = "uk_edu_operation_condition_version", columnNames = {"run_id", "condition_version"}))
public class OperationAgentCondition extends BaseEntity {
	@Column(name = "run_id", nullable = false, length = 64) private String runId;
	@Column(name = "condition_version", nullable = false) private Integer conditionVersion;
	@Column(name = "condition_text", nullable = false, columnDefinition = "text") private String conditionText;
	@Column(name = "impact_analysis", nullable = false, columnDefinition = "text") private String impactAnalysis;
	@Column(name = "parsed_condition_json", nullable = false, columnDefinition = "text") private String parsedConditionJson = "{}";
	@Column(name = "submitted_by", nullable = false, length = 128) private String submittedBy;
}
