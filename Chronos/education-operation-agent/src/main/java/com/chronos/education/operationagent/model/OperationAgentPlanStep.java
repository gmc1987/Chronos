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
@Table(name = "edu_operation_agent_plan_step", uniqueConstraints = @UniqueConstraint(
		name = "uk_edu_operation_plan_step", columnNames = {"plan_id", "step_order"}))
public class OperationAgentPlanStep extends BaseEntity {
	@Column(name = "plan_id", nullable = false, length = 64) private String planId;
	@Column(name = "step_order", nullable = false) private Integer stepOrder;
	@Column(name = "step_code", nullable = false, length = 64) private String stepCode;
	@Column(name = "step_name", nullable = false, length = 128) private String stepName;
	@Column(name = "description", length = 1000) private String description;
	@Column(name = "tool_code", nullable = false, length = 96) private String toolCode;
	@Column(name = "business_stage_order") private Integer businessStageOrder;
	@Column(name = "business_stage_code", length = 48) private String businessStageCode;
	@Column(name = "business_stage_name", length = 128) private String businessStageName;
	@Column(name = "risk_level", nullable = false, length = 16) private String riskLevel = "LOW";
	@Column(name = "approval_required", nullable = false) private Boolean approvalRequired = false;
	@Column(name = "success_criteria", length = 1000) private String successCriteria;
	@Column(name = "tool_config_json", nullable = false, columnDefinition = "text") private String toolConfigJson = "{}";
}
