package com.chronos.message.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** SNAPSHOT 模式在发布时固化的收件人，用于稳定权限判断和阅读率分母。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "msg_publication_recipient",
		comment = "通知公告收件人快照",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_msg_publication_recipient",
				columnNames = { "publication_id", "username" }))
public class PublicationRecipient extends BaseEntity {
	@Column(name = "publication_id", length = 64, nullable = false)
	private String publicationId;

	@Column(name = "username", length = 128, nullable = false)
	private String username;
}
