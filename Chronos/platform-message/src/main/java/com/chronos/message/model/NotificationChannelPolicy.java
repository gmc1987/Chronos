package com.chronos.message.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 平台级渠道开关和发送频率上限。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "msg_channel_policy")
public class NotificationChannelPolicy extends BaseEntity {

	@Column(name = "channel", length = 32, nullable = false, unique = true)
	private String channel;

	@Column(name = "enabled", nullable = false)
	private Boolean enabled = false;

	@Column(name = "max_per_minute", nullable = false)
	private Integer maxPerMinute = 60;

	@Column(name = "max_per_day", nullable = false)
	private Integer maxPerDay = 1000;

	@Column(name = "min_interval_seconds", nullable = false)
	private Integer minIntervalSeconds = 0;
}
