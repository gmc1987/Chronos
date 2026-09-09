package com.chronos.model.workflow;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 门户站内通知。外部短信、邮件等渠道后续消费同一 Outbox 事件即可扩展。 */
@Entity
@Table(
		name = "wf_notification",
		indexes = {
			@Index(name = "idx_wf_notification_recipient", columnList = "recipient,read_at"),
			@Index(name = "idx_wf_notification_task", columnList = "task_id")
		}
)
@Getter
@Setter
public class WorkflowNotification extends BaseEntity {
	@Column(nullable = false, length = 128)
	private String recipient;

	@Column(name = "notification_type", nullable = false, length = 50)
	private String notificationType;

	@Column(nullable = false, length = 200)
	private String title;

	/** PostgreSQL 长文本必须显式使用 text，不能使用会被 Hibernate 映射成 OID 的 @Lob。 */
	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(name = "instance_id", length = 64)
	private String instanceId;

	@Column(name = "task_id", length = 64)
	private String taskId;

	@Column(name = "read_at")
	private LocalDateTime readAt;

	@Column(name = "source_event_id", nullable = false, length = 64, unique = true)
	private String sourceEventId;
}
