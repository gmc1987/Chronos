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

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "msg_publication_read",
		comment = "通知公告阅读回执",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_msg_publication_read",
				columnNames = { "publication_id", "username" }))
public class PublicationRead extends BaseEntity {
	@Column(name = "publication_id", length = 64, nullable = false)
	private String publicationId;

	@Column(name = "username", length = 128, nullable = false)
	private String username;

	@Column(name = "read_at", nullable = false)
	private LocalDateTime readAt;
}
