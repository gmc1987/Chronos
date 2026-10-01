package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Small, replayable execution trace; large prompts and payloads are excluded. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "agent_step",
		uniqueConstraints = {
			@UniqueConstraint(
					name = "uk_agent_step_run_no",
					columnNames = { "run_id", "step_no" }),
			@UniqueConstraint(
					name = "uk_agent_step_run_key",
					columnNames = { "run_id", "step_key" })
		})
public class AgentStep extends BaseEntity {
	@Column(name = "run_id", length = 64, nullable = false)
	private String runId;

	@Column(name = "step_no", nullable = false)
	private Integer stepNo;

	@Column(name = "skill_code", length = 64, nullable = false)
	private String skillCode;

	@Column(name = "tool_code", length = 100)
	private String toolCode;

	@Column(name = "plan_version", nullable = false)
	private Integer planVersion;

	@Column(name = "step_key", length = 128, nullable = false)
	private String stepKey;

	@Column(name = "state", length = 24, nullable = false)
	private String state = "QUEUED";

	@Column(name = "input_digest", length = 64, nullable = false)
	private String inputDigest;

	@Column(name = "output_summary_json", columnDefinition = "text")
	private String outputSummaryJson;

	@Column(name = "result_code", length = 64)
	private String resultCode;

	@Column(name = "duration_ms")
	private Long durationMs;

	@Column(name = "started_at")
	private LocalDateTime startedAt;

	@Column(name = "finished_at")
	private LocalDateTime finishedAt;
}
