package com.chronos.message.model;

import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 可靠触达 Outbox；站内、邮件、短信等渠道可以独立消费并重试。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "msg_publication_delivery",
		comment = "通知公告触达任务",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_msg_publication_delivery",
				columnNames = { "publication_id", "username", "channel" }))
public class PublicationDelivery extends BaseEntity {
	@Column(name = "publication_id", length = 64, nullable = false)
	private String publicationId;

	@Column(name = "username", length = 128, nullable = false)
	private String username;

	@Column(name = "channel", length = 32, nullable = false)
	private String channel = "IN_APP";

	@Column(name = "status", length = 32, nullable = false)
	private String status = "PENDING";

	@Column(name = "attempt_count", nullable = false)
	private Integer attemptCount = 0;

	@Column(name = "next_attempt_at")
	private LocalDateTime nextAttemptAt;

	@Column(name = "delivered_at")
	private LocalDateTime deliveredAt;

	@Column(name = "last_error", length = 1000)
	private String lastError;
}
