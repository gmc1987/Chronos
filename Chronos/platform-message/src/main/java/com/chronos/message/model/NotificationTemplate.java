package com.chronos.message.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 渠道无关的通知模板，发送器负责把渲染结果适配到具体供应商。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "msg_notification_template",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_msg_notification_template",
				columnNames = { "template_code", "channel" }))
public class NotificationTemplate extends BaseEntity {

	@Column(name = "template_code", length = 100, nullable = false)
	private String templateCode;

	@Column(name = "channel", length = 32, nullable = false)
	private String channel;

	@Column(name = "template_name", length = 200, nullable = false)
	private String templateName;

	@Column(name = "subject_template", length = 500)
	private String subjectTemplate;

	@Column(name = "content_template", columnDefinition = "text", nullable = false)
	private String contentTemplate;

	@Column(name = "enabled", nullable = false)
	private Boolean enabled = true;
}
