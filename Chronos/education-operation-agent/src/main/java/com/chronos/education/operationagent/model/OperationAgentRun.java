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
@Table(name = "edu_operation_agent_run")
public class OperationAgentRun extends BaseEntity {
	/** 删除计划后保留运行快照与审计证据，因此历史运行允许解除计划外键。 */
	@Column(name = "plan_id", length = 64) private String planId;
	@Column(name = "plan_version", nullable = false) private Integer planVersion;
	@Column(name = "owner_username", nullable = false, length = 128) private String ownerUsername;
	@Column(name = "status", nullable = false, length = 32) private String status = "QUEUED";
	@Column(name = "current_step_order") private Integer currentStepOrder;
	@Column(name = "current_step_name", length = 128) private String currentStepName;
	@Column(name = "next_action", length = 500) private String nextAction;
	@Column(name = "pause_requested", nullable = false) private Boolean pauseRequested = false;
	@Column(name = "condition_version", nullable = false) private Integer conditionVersion = 1;
	@Column(name = "plan_snapshot_json", nullable = false, columnDefinition = "text") private String planSnapshotJson;
	@Column(name = "summary", length = 1000) private String summary;
	@Column(name = "started_at") private LocalDateTime startedAt;
	@Column(name = "paused_at") private LocalDateTime pausedAt;
	@Column(name = "completed_at") private LocalDateTime completedAt;
	@Column(name = "error_message", length = 1000) private String errorMessage;
	@Version @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;
}
