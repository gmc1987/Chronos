package com.chronos.model.workflow;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "wf_outbox", indexes = @Index(name = "idx_wf_outbox_dispatch",
		columnList = "status,next_attempt_at"))
@Getter
@Setter
public class WorkflowOutbox extends BaseEntity {
	@Column(name = "event_type", nullable = false, length = 80)
	private String eventType;

	@Column(name = "aggregate_id", length = 64)
	private String aggregateId;

	/** 事件载荷使用数据库 text；@Lob String 在 PostgreSQL 上会被推断为 OID。 */
	@Column(name = "payload_json", nullable = false, columnDefinition = "text")
	private String payloadJson;

	@Column(nullable = false, length = 30)
	private String status = "PENDING";

	@Column(nullable = false)
	private Integer attempts = 0;

	@Column(name = "next_attempt_at")
	private LocalDateTime nextAttemptAt;

	@Column(name = "sent_at")
	private LocalDateTime sentAt;

	@Column(name = "last_error", length = 1000)
	private String lastError;

	/** 业务幂等键，防止调度器重复扫描时产生相同通知。 */
	// JPA 保持可平滑升级；生产迁移脚本在回填历史数据后再收紧 NOT NULL。
	@Column(name = "deduplication_key", length = 200, unique = true)
	private String deduplicationKey;
}
