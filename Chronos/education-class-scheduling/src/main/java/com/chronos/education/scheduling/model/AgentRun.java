package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Durable, tenant-owned state for one AI scheduling conversation. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "agent_run",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_agent_run_owner_request",
				columnNames = { "owner_username", "client_request_id" }))
public class AgentRun extends BaseEntity {
	@Column(name = "client_request_id", length = 128, nullable = false)
	private String clientRequestId;

	@Column(name = "agent_code", length = 64, nullable = false)
	private String agentCode = "EDUCATION_SCHEDULING_V1";

	@Column(name = "owner_username", length = 128, nullable = false)
	private String ownerUsername;

	@Column(name = "school_id", length = 64)
	private String schoolId;

	@Column(name = "semester_code", length = 32, nullable = false)
	private String semesterCode;

	@Column(name = "status", length = 32, nullable = false)
	private String status = "DRAFT";

	@Column(name = "plan_version", nullable = false)
	private Integer planVersion = 1;

	@Column(name = "request_hash", length = 64, nullable = false)
	private String requestHash;

	@Column(name = "request_ciphertext", columnDefinition = "text")
	private String requestCiphertext;

	@Column(name = "parsed_plan_json", columnDefinition = "text")
	private String parsedPlanJson;

	@Column(name = "confirmed_plan_json", columnDefinition = "text")
	private String confirmedPlanJson;

	@Column(name = "related_job_id", length = 64)
	private String relatedJobId;

	@Column(name = "model_id", length = 64)
	private String modelId;

	@Column(name = "error_code", length = 64)
	private String errorCode;

	@Column(name = "error_message", length = 500)
	private String errorMessage;

	@Column(name = "expires_at")
	private LocalDateTime expiresAt;

	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
