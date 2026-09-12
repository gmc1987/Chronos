package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 持久化自动排课后台任务，避免大规模生成依赖单个 HTTP 长连接。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_schedule_generation_job")
public class ScheduleGenerationJob extends BaseEntity {
	@Column(name = "semester_code", length = 32, nullable = false)
	private String semesterCode;

	@Column(name = "request_json", columnDefinition = "text", nullable = false)
	private String requestJson;

	@Column(name = "status", length = 24, nullable = false)
	private String status = "QUEUED";

	@Column(name = "progress", nullable = false)
	private Integer progress = 0;

	@Column(name = "result_candidate_ids", columnDefinition = "text")
	private String resultCandidateIds;

	@Column(name = "error_message", length = 2000)
	private String errorMessage;

	@Column(name = "requested_by", length = 128, nullable = false)
	private String requestedBy;

	@Column(name = "started_at")
	private LocalDateTime startedAt;

	@Column(name = "finished_at")
	private LocalDateTime finishedAt;

	@Version
	@Column(name = "lock_version", nullable = false)
	private Long lockVersion = 0L;
}
