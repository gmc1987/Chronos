package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.EducationDomainOutboxRepository;
import com.chronos.education.scheduling.model.EducationDomainOutbox;
import com.chronos.education.scheduling.model.dto.ResearchErrorDtos.WrongAnswerConfirmed;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 发布并消费教育领域事件，提供持久化、幂等、重试和失败留痕。 */
@Service
public class EducationDomainEventService {
	private static final String WRONG_ANSWER_CONFIRMED = "WrongAnswerConfirmed";
	private static final int MAX_ATTEMPTS = 8;
	private final EducationDomainOutboxRepository outbox;
	private final ResearchErrorService errorRecords;
	private final ObjectMapper json;

	public EducationDomainEventService(
			EducationDomainOutboxRepository outbox,
			ResearchErrorService errorRecords,
			ObjectMapper json) {
		this.outbox = outbox;
		this.errorRecords = errorRecords;
		this.json = json;
	}

	@Transactional
	public EducationDomainOutbox enqueueWrongAnswer(
			WrongAnswerConfirmed event,
			String actor) {
		return outbox.findByEventId(event.eventId()).orElseGet(() -> {
			try {
				EducationDomainOutbox value = new EducationDomainOutbox();
				// 显式填充审计字段，使事件在非 Web 调用或审计上下文缺失时仍可可靠落库。
				LocalDateTime now = LocalDateTime.now();
				value.setCreateBy(actor);
				value.setCreateTime(now);
				value.setEventId(event.eventId());
				value.setEventType(WRONG_ANSWER_CONFIRMED);
				value.setAggregateId(event.sourceItemId());
				value.setPayloadJson(json.writeValueAsString(event));
				value.setActor(actor);
				value.setNextAttemptAt(now);
				return outbox.save(value);
			} catch (Exception exception) {
				throw new IllegalStateException("错题事件写入 Outbox 失败", exception);
			}
		});
	}

	@Scheduled(fixedDelayString = "${chronos.education.outbox.delay-ms:3000}")
	@Transactional
	public void dispatch() {
		for (EducationDomainOutbox event : outbox.lockDispatchBatch(LocalDateTime.now())) {
			try {
				deliver(event);
				event.setStatus("PROCESSED");
				event.setProcessedAt(LocalDateTime.now());
				event.setLastError(null);
			} catch (Exception exception) {
				int attempts = event.getAttempts() + 1;
				event.setAttempts(attempts);
				event.setLastError(truncate(exception.getMessage(), 1000));
				if (attempts >= MAX_ATTEMPTS) {
					event.setStatus("DEAD");
				} else {
					// 指数退避限制在一小时，避免故障期间持续打满数据库和日志。
					long delaySeconds = Math.min(3600L, 1L << Math.min(attempts, 12));
					event.setNextAttemptAt(LocalDateTime.now().plusSeconds(delaySeconds));
				}
			}
			outbox.save(event);
		}
	}

	@Transactional(readOnly = true)
	public Page<EducationDomainOutbox> deadEvents(int page, int size) {
		return outbox.findByStatusOrderByCreateTimeDesc(
				"DEAD",
				PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
	}

	@Transactional
	public EducationDomainOutbox retry(String id) {
		EducationDomainOutbox event = requireDead(id);
		event.setStatus("PENDING");
		event.setAttempts(0);
		event.setLastError(null);
		event.setNextAttemptAt(LocalDateTime.now());
		return outbox.save(event);
	}

	@Transactional
	public EducationDomainOutbox ignore(String id) {
		EducationDomainOutbox event = requireDead(id);
		event.setStatus("IGNORED");
		return outbox.save(event);
	}

	private EducationDomainOutbox requireDead(String id) {
		EducationDomainOutbox event = outbox.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("教育领域死信不存在"));
		if (!"DEAD".equals(event.getStatus())) {
			throw new IllegalStateException("只有死信事件可以执行该操作");
		}
		return event;
	}

	private void deliver(EducationDomainOutbox event) throws Exception {
		if (!WRONG_ANSWER_CONFIRMED.equals(event.getEventType())) {
			throw new IllegalArgumentException("不支持的教育领域事件：" + event.getEventType());
		}
		WrongAnswerConfirmed payload = json.readValue(
				event.getPayloadJson(),
				WrongAnswerConfirmed.class);
		var authentication = UsernamePasswordAuthenticationToken.authenticated(
				event.getActor(),
				"",
				List.of());
		errorRecords.onTrustedWrongAnswerConfirmed(payload, authentication);
	}

	private String truncate(String value, int maxLength) {
		if (value == null) {
			return "未知错误";
		}
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}
}
