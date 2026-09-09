package com.chronos.message.config;

import java.util.List;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.chronos.message.Idao.INotificationChannelPolicyRepository;
import com.chronos.message.model.NotificationChannelPolicy;

/** 为新环境建立安全的渠道默认值；外部渠道在配置发送器前保持关闭。 */
@Configuration
public class NotificationPolicySeedConfig {

	@Bean
	ApplicationRunner notificationPolicySeed(INotificationChannelPolicyRepository policies) {
		return arguments -> List.of(
				policy("IN_APP", true, 10000, 1000000, 0),
				policy("EMAIL", false, 120, 10000, 1),
				policy("SMS", false, 60, 3000, 2),
				policy("WE_COM", false, 300, 30000, 0))
				.forEach(candidate -> policies.findByChannel(candidate.getChannel())
						.orElseGet(() -> policies.save(candidate)));
	}

	private NotificationChannelPolicy policy(
			String channel,
			boolean enabled,
			int maxPerMinute,
			int maxPerDay,
			int minIntervalSeconds) {
		NotificationChannelPolicy value = new NotificationChannelPolicy();
		value.setChannel(channel);
		value.setEnabled(enabled);
		value.setMaxPerMinute(maxPerMinute);
		value.setMaxPerDay(maxPerDay);
		value.setMinIntervalSeconds(minIntervalSeconds);
		return value;
	}
}
