package com.chronos.model.workflow;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "wf_incident", indexes = {
		@Index(name = "idx_wf_incident_status", columnList = "status,next_retry_at"),
		@Index(name = "idx_wf_incident_instance", columnList = "instance_id") },
		uniqueConstraints = @UniqueConstraint(
				name = "uk_wf_incident_engine_job",
				columnNames = "engine_job_id"
		)
)
@Getter
@Setter
public class WorkflowIncident extends BaseEntity {
	@Column(name = "instance_id", length = 64)
	private String instanceId;

	@Column(name = "node_key", length = 100)
	private String nodeKey;

	/** Flowable 死信作业 ID，用于事故同步去重和人工恢复。 */
	@Column(name = "engine_job_id", length = 64)
	private String engineJobId;

	@Column(name = "engine_instance_id", length = 64)
	private String engineInstanceId;

	@Column(name = "execution_id", length = 64)
	private String executionId;

	@Column(name = "incident_type", nullable = false, length = 40)
	private String incidentType;

	@Column(nullable = false, length = 30)
	private String status = "OPEN";

	@Column(name = "retry_count", nullable = false)
	private Integer retryCount = 0;

	@Column(name = "next_retry_at")
	private LocalDateTime nextRetryAt;

	@Column(name = "error_message", columnDefinition = "text")
	private String errorMessage;

	@Column(name = "context_json", columnDefinition = "text")
	private String contextJson;

	@Column(name = "resolved_by", length = 128)
	private String resolvedBy;

	@Column(name = "resolved_at")
	private LocalDateTime resolvedAt;

	@Column(name = "resolution", length = 1000)
	private String resolution;

	@Version
	@Column(name = "lock_version", nullable = false, columnDefinition = "bigint default 0")
	private Long lockVersion = 0L;
}
