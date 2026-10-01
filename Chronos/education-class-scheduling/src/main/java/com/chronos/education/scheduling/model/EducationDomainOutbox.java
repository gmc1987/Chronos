package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 教育领域跨模块事件 Outbox；业务事实与事件在同一数据库事务中提交。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_domain_outbox", indexes = {
		@Index(name = "idx_edu_domain_outbox_dispatch", columnList = "status,next_attempt_at") })
public class EducationDomainOutbox extends BaseEntity {
	@Column(name = "event_id", nullable = false, unique = true, length = 128)
	private String eventId;

	@Column(name = "event_type", nullable = false, length = 80)
	private String eventType;

	@Column(name = "aggregate_id", length = 64)
	private String aggregateId;

	@Column(name = "payload_json", nullable = false, columnDefinition = "text")
	private String payloadJson;

	@Column(name = "actor", nullable = false, length = 128)
	private String actor;

	@Column(nullable = false, length = 24)
	private String status = "PENDING";

	@Column(nullable = false)
	private int attempts;

	@Column(name = "next_attempt_at", nullable = false)
	private LocalDateTime nextAttemptAt;

	@Column(name = "processed_at")
	private LocalDateTime processedAt;

	@Column(name = "last_error", length = 1000)
	private String lastError;

	/** 防止后台投递与管理员重试、忽略操作同时覆盖事件状态。 */
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
