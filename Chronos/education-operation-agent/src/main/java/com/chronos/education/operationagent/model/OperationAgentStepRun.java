package com.chronos.education.operationagent.model;

import java.time.LocalDateTime;
import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_operation_agent_step_run", uniqueConstraints = @UniqueConstraint(
		name = "uk_edu_operation_run_step", columnNames = {"run_id", "step_order"}))
public class OperationAgentStepRun extends BaseEntity {
	@Column(name = "run_id", nullable = false, length = 64) private String runId;
	@Column(name = "plan_step_id", nullable = false, length = 64) private String planStepId;
	@Column(name = "step_order", nullable = false) private Integer stepOrder;
	@Column(name = "step_name", nullable = false, length = 128) private String stepName;
	@Column(name = "tool_code", nullable = false, length = 96) private String toolCode;
	@Column(name = "business_stage_order") private Integer businessStageOrder;
	@Column(name = "business_stage_code", length = 48) private String businessStageCode;
	@Column(name = "business_stage_name", length = 128) private String businessStageName;
	@Column(name = "status", nullable = false, length = 32) private String status = "QUEUED";
	@Column(name = "progress", nullable = false) private Integer progress = 0;
	@Column(name = "attempt_no", nullable = false) private Integer attemptNo = 0;
	@Column(name = "approval_required", nullable = false) private Boolean approvalRequired = false;
	@Column(name = "approved_by", length = 128) private String approvedBy;
	@Column(name = "approved_at") private LocalDateTime approvedAt;
	@Column(name = "message", length = 1000) private String message;
	@Column(name = "result_json", columnDefinition = "text") private String resultJson;
	@Column(name = "config_overrides_json", nullable = false, columnDefinition = "text") private String configOverridesJson = "{}";
	@Column(name = "started_at") private LocalDateTime startedAt;
	@Column(name = "completed_at") private LocalDateTime completedAt;
	@Version @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;
}
