package com.chronos.message.service.impl;

import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.message.Idao.INotificationChannelPolicyRepository;
import com.chronos.message.Idao.INotificationChannelPreferenceRepository;
import com.chronos.message.Idao.INotificationTemplateRepository;
import com.chronos.message.model.NotificationChannelPolicy;
import com.chronos.message.model.NotificationChannelPreference;
import com.chronos.message.model.NotificationTemplate;

/** 管理渠道偏好、模板和平台级频率策略，不包含任何供应商实现。 */
@Service
public class NotificationConfigurationService {

	private static final List<String> CHANNELS = List.of("IN_APP", "EMAIL", "SMS", "WE_COM");

	private final INotificationChannelPreferenceRepository preferences;
	private final INotificationTemplateRepository templates;
	private final INotificationChannelPolicyRepository policies;

	public NotificationConfigurationService(
			INotificationChannelPreferenceRepository preferences,
			INotificationTemplateRepository templates,
			INotificationChannelPolicyRepository policies) {
		this.preferences = preferences;
		this.templates = templates;
		this.policies = policies;
	}

	@Transactional(readOnly = true)
	public List<NotificationChannelPreference> preferences(String username) {
		return preferences.findByUsernameOrderByChannelAsc(username);
	}

	@Transactional
	public NotificationChannelPreference savePreference(
			String username,
			NotificationChannelPreference command) {
		String channel = channel(command.getChannel());
		NotificationChannelPreference value = preferences.findByUsernameAndChannel(username, channel)
				.orElseGet(NotificationChannelPreference::new);
		value.setUsername(username);
		value.setChannel(channel);
		value.setEnabled(!Boolean.FALSE.equals(command.getEnabled()));
		value.setQuietStart(command.getQuietStart());
		value.setQuietEnd(command.getQuietEnd());
		value.setDailyLimit(limit(command.getDailyLimit(), 1, 1000, null));
		return preferences.save(value);
	}

	@Transactional(readOnly = true)
	public Page<NotificationTemplate> templates(String keyword, String channel, Pageable pageable) {
		return templates.findAll((root, query, cb) -> {
			var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
			if (keyword != null && !keyword.isBlank()) {
				String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
				predicates.add(cb.or(
						cb.like(cb.lower(root.get("templateCode")), pattern),
						cb.like(cb.lower(root.get("templateName")), pattern)));
			}
			if (channel != null && !channel.isBlank()) {
				predicates.add(cb.equal(root.get("channel"), channel(channel)));
			}
			return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
		}, pageable);
	}

	@Transactional
	public NotificationTemplate saveTemplate(String id, NotificationTemplate command) {
		NotificationTemplate value = id == null
				? new NotificationTemplate()
				: templates.findById(id).orElseThrow(() -> new IllegalArgumentException("消息模板不存在"));
		if (command.getTemplateCode() == null || command.getTemplateCode().isBlank()
				|| command.getTemplateName() == null || command.getTemplateName().isBlank()
				|| command.getContentTemplate() == null || command.getContentTemplate().isBlank()) {
			throw new IllegalArgumentException("模板编码、名称和内容不能为空");
		}
		String code = command.getTemplateCode().trim().toUpperCase(Locale.ROOT);
		String channel = channel(command.getChannel());
		templates.findByTemplateCodeAndChannel(code, channel)
				.filter(existing -> !existing.getId().equals(id))
				.ifPresent(existing -> {
					throw new IllegalArgumentException("同一渠道的模板编码不能重复");
				});
		value.setTemplateCode(code);
		value.setChannel(channel);
		value.setTemplateName(command.getTemplateName().trim());
		value.setSubjectTemplate(command.getSubjectTemplate());
		value.setContentTemplate(command.getContentTemplate());
		value.setEnabled(!Boolean.FALSE.equals(command.getEnabled()));
		return templates.save(value);
	}

	@Transactional
	public void deleteTemplate(String id) {
		templates.deleteById(id);
	}

	@Transactional(readOnly = true)
	public List<NotificationChannelPolicy> policies() {
		return policies.findAll().stream()
				.sorted(java.util.Comparator.comparing(NotificationChannelPolicy::getChannel))
				.toList();
	}

	@Transactional
	public NotificationChannelPolicy savePolicy(NotificationChannelPolicy command) {
		String channel = channel(command.getChannel());
		NotificationChannelPolicy value = policies.findByChannel(channel)
				.orElseGet(NotificationChannelPolicy::new);
		value.setChannel(channel);
		value.setEnabled("IN_APP".equals(channel) || Boolean.TRUE.equals(command.getEnabled()));
		value.setMaxPerMinute(limit(command.getMaxPerMinute(), 1, 100000, 60));
		value.setMaxPerDay(limit(command.getMaxPerDay(), 1, 1000000, 1000));
		value.setMinIntervalSeconds(limit(command.getMinIntervalSeconds(), 0, 86400, 0));
		return policies.save(value);
	}

	public boolean inQuietHours(NotificationChannelPreference preference, LocalTime now) {
		if (preference == null || preference.getQuietStart() == null || preference.getQuietEnd() == null) {
			return false;
		}
		if (preference.getQuietStart().equals(preference.getQuietEnd())) {
			return true;
		}
		return preference.getQuietStart().isBefore(preference.getQuietEnd())
				? !now.isBefore(preference.getQuietStart()) && now.isBefore(preference.getQuietEnd())
				: !now.isBefore(preference.getQuietStart()) || now.isBefore(preference.getQuietEnd());
	}

	private String channel(String value) {
		String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
		if (!CHANNELS.contains(normalized)) {
			throw new IllegalArgumentException("不支持的消息渠道");
		}
		return normalized;
	}

	private Integer limit(Integer value, int minimum, int maximum, Integer defaultValue) {
		if (value == null) {
			return defaultValue;
		}
		if (value < minimum || value > maximum) {
			throw new IllegalArgumentException("频率限制参数超出允许范围");
		}
		return value;
	}
}
