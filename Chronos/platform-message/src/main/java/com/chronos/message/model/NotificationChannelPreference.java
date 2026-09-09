package com.chronos.message.model;

import java.time.LocalTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 用户对各通知渠道的接收偏好和免打扰时段。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "msg_channel_preference",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_msg_channel_preference",
				columnNames = { "username", "channel" }))
public class NotificationChannelPreference extends BaseEntity {

	@Column(name = "username", length = 128, nullable = false)
	private String username;

	@Column(name = "channel", length = 32, nullable = false)
	private String channel;

	@Column(name = "enabled", nullable = false)
	private Boolean enabled = true;

	@Column(name = "quiet_start")
	private LocalTime quietStart;

	@Column(name = "quiet_end")
	private LocalTime quietEnd;

	@Column(name = "daily_limit")
	private Integer dailyLimit;
}
