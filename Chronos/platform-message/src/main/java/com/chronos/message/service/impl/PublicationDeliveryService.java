package com.chronos.message.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.message.Idao.IPublicationDeliveryRepository;
import com.chronos.message.Idao.IPublicationRepository;
import com.chronos.message.Idao.INotificationChannelPolicyRepository;
import com.chronos.message.Idao.INotificationChannelPreferenceRepository;
import com.chronos.message.model.PublicationDelivery;
import com.chronos.message.service.channel.PublicationChannelSender;

@Service
public class PublicationDeliveryService {
	private enum DeliveryDecision {
		ALLOW,
		DEFER,
		SUPPRESS
	}
	private final IPublicationDeliveryRepository deliveries;
	private final IPublicationRepository publications;
	private final Map<String, PublicationChannelSender> senders;
	private final INotificationChannelPolicyRepository policies;
	private final INotificationChannelPreferenceRepository preferences;
	private final NotificationConfigurationService configuration;

	public PublicationDeliveryService(
			IPublicationDeliveryRepository deliveries,
			IPublicationRepository publications,
			List<PublicationChannelSender> senders,
			INotificationChannelPolicyRepository policies,
			INotificationChannelPreferenceRepository preferences,
			NotificationConfigurationService configuration) {
		this.deliveries = deliveries;
		this.publications = publications;
		this.senders = senders.stream().collect(Collectors.toMap(
				PublicationChannelSender::channel,
				Function.identity()));
		this.policies = policies;
		this.preferences = preferences;
		this.configuration = configuration;
	}

	@Transactional
	public void dispatchBatch() {
		LocalDateTime now = LocalDateTime.now();
		deliveries.findByStatusAndNextAttemptAtLessThanEqual(
				"PENDING",
				now,
				PageRequest.of(0, 100)).forEach(delivery -> dispatch(delivery, now));
	}

	private void dispatch(PublicationDelivery delivery, LocalDateTime now) {
		try {
			DeliveryDecision decision = decision(delivery, now);
			if (decision == DeliveryDecision.SUPPRESS) {
				// 明确停用的渠道和用户退订不是故障，终止任务避免永久轮询。
				delivery.setStatus("SUPPRESSED");
				delivery.setLastError("channel disabled or user opted out");
				deliveries.save(delivery);
				return;
			}
			if (decision == DeliveryDecision.DEFER) {
				// 限流和免打扰属于延期，不计入失败重试次数。
				delivery.setNextAttemptAt(now.plusMinutes(1));
				deliveries.save(delivery);
				return;
			}
			var publication = publications.findById(delivery.getPublicationId())
					.orElseThrow(() -> new IllegalArgumentException("通知公告不存在"));
			PublicationChannelSender sender = senders.get(delivery.getChannel());
			if (sender == null) {
				throw new IllegalStateException("消息渠道未配置: " + delivery.getChannel());
			}
			sender.send(publication, delivery.getUsername());
			delivery.setStatus("DELIVERED");
			delivery.setDeliveredAt(now);
			delivery.setLastError(null);
		} catch (RuntimeException exception) {
			int attempts = delivery.getAttemptCount() + 1;
			delivery.setAttemptCount(attempts);
			delivery.setLastError(limit(exception.getMessage()));
			if (attempts >= 5) {
				delivery.setStatus("DEAD");
			} else {
				delivery.setNextAttemptAt(now.plusMinutes(1L << Math.min(attempts, 5)));
			}
		}
		deliveries.save(delivery);
	}

	private DeliveryDecision decision(PublicationDelivery delivery, LocalDateTime now) {
		String channel = delivery.getChannel();
		var policy = policies.findByChannel(channel).orElse(null);
		if (policy == null) {
			return "IN_APP".equals(channel) ? DeliveryDecision.ALLOW : DeliveryDecision.SUPPRESS;
		}
		if (!Boolean.TRUE.equals(policy.getEnabled()) && !"IN_APP".equals(channel)) {
			return DeliveryDecision.SUPPRESS;
		}
		long minuteCount = deliveries.countByChannelAndStatusAndDeliveredAtAfter(
				channel,
				"DELIVERED",
				now.minusMinutes(1));
		if (minuteCount >= policy.getMaxPerMinute()) {
			return DeliveryDecision.DEFER;
		}
		var preference = preferences.findByUsernameAndChannel(delivery.getUsername(), channel).orElse(null);
		if (preference != null && Boolean.FALSE.equals(preference.getEnabled())) {
			return DeliveryDecision.SUPPRESS;
		}
		if (configuration.inQuietHours(preference, now.toLocalTime())) {
			return DeliveryDecision.DEFER;
		}
		int dailyLimit = preference != null && preference.getDailyLimit() != null
				? Math.min(preference.getDailyLimit(), policy.getMaxPerDay())
				: policy.getMaxPerDay();
		long dailyCount = deliveries.countByUsernameAndChannelAndStatusAndDeliveredAtAfter(
				delivery.getUsername(),
				channel,
				"DELIVERED",
				now.toLocalDate().atStartOfDay());
		if (dailyCount >= dailyLimit) {
			return DeliveryDecision.DEFER;
		}
		boolean intervalPassed = deliveries.findTopByUsernameAndChannelAndStatusOrderByDeliveredAtDesc(
						delivery.getUsername(),
						channel,
						"DELIVERED")
				.map(previous -> previous.getDeliveredAt() == null
						|| !previous.getDeliveredAt().plusSeconds(policy.getMinIntervalSeconds()).isAfter(now))
				.orElse(true);
		return intervalPassed ? DeliveryDecision.ALLOW : DeliveryDecision.DEFER;
	}

	@Transactional
	public PublicationDelivery retryDead(String id) {
		PublicationDelivery delivery = deliveries.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("投递任务不存在"));
		if (!"DEAD".equals(delivery.getStatus())) {
			throw new IllegalStateException("只有死信任务允许人工重试");
		}
		delivery.setStatus("PENDING");
		delivery.setAttemptCount(0);
		delivery.setNextAttemptAt(LocalDateTime.now());
		delivery.setLastError(null);
		return deliveries.save(delivery);
	}

	private String limit(String value) {
		if (value == null) {
			return "unknown error";
		}
		return value.length() <= 1000 ? value : value.substring(0, 1000);
	}
}
