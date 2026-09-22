package com.chronos.education.grade.model;

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

/** 成绩更正工作流已完成、业务成绩版本回写失败时的恢复台账。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
	name = "edu_grade_change_incident",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_grade_change_incident_request",
		columnNames = "change_request_id"))
public class GradeChangeIncident extends BaseEntity {
	@Column(name = "change_request_id", nullable = false, length = 64)
	private String changeRequestId;

	@Column(name = "workflow_instance_id", nullable = false, length = 64)
	private String workflowInstanceId;

	@Column(nullable = false, length = 24)
	private String status = "OPEN";

	@Column(name = "retry_count", nullable = false)
	private Integer retryCount = 0;

	@Column(name = "last_error", nullable = false, length = 2000)
	private String lastError;

	@Column(name = "last_retry_by", length = 128)
	private String lastRetryBy;

	@Column(name = "last_retry_at")
	private LocalDateTime lastRetryAt;

	@Column(name = "resolved_by", length = 128)
	private String resolvedBy;

	@Column(name = "resolved_at")
	private LocalDateTime resolvedAt;

	@Column(name = "resolution_note", length = 1000)
	private String resolutionNote;

	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
