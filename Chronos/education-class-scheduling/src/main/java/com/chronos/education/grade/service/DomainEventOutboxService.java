package com.chronos.education.grade.service;

import com.chronos.education.grade.dao.DomainEventOutboxRepository;
import com.chronos.education.grade.model.DomainEventOutbox;
import com.chronos.education.grade.dto.GradeSourceEventContracts.CourseGradesPublishedV1;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DomainEventOutboxService {
	private final DomainEventOutboxRepository outbox;
	private final ObjectMapper json;

	public DomainEventOutboxService(DomainEventOutboxRepository outbox, ObjectMapper json) {
		this.outbox = outbox;
		this.json = json;
	}

	@Transactional
	public void enqueue(CourseGradesPublishedV1 event) {
		enqueue(event.eventType(), event.gradebookId(), event.eventId(), event);
	}

	@Transactional
	public void enqueue(String eventType, String aggregateId, String deduplicationKey, Object event) {
		if (outbox.existsByDeduplicationKey(deduplicationKey)) {
			return;
		}
		DomainEventOutbox record = new DomainEventOutbox();
		record.setEventType(eventType);
		record.setAggregateId(aggregateId);
		record.setPayloadJson(write(event));
		record.setDeduplicationKey(deduplicationKey);
		record.setNextAttemptAt(LocalDateTime.now());
		outbox.save(record);
	}

	@Transactional
	public List<DomainEventOutbox> claimBatch(int batchSize, LocalDateTime now, long leaseSeconds) {
		int normalizedBatchSize = Math.min(Math.max(1, batchSize), 200);
		List<DomainEventOutbox> claimed = outbox.findDispatchCandidates(
				now,
				PageRequest.of(0, normalizedBatchSize));
		LocalDateTime leaseUntil = now.plusSeconds(Math.max(1, leaseSeconds));
		for (DomainEventOutbox event : claimed) {
			event.setStatus("PROCESSING");
			event.setLeaseUntil(leaseUntil);
			event.setClaimToken(UUID.randomUUID().toString());
			outbox.save(event);
		}
		return claimed;
	}

	@Transactional
	public boolean markSent(String id, String claimToken) {
		DomainEventOutbox event = activeClaim(id, claimToken);
		if (event == null) {
			return false;
		}
		event.setStatus("SENT");
		event.setSentAt(LocalDateTime.now());
		event.setLeaseUntil(null);
		event.setClaimToken(null);
		event.setLastError(null);
		return true;
	}

	@Transactional
	public boolean markFailed(String id, String claimToken, Exception failure, int maxAttempts) {
		DomainEventOutbox event = activeClaim(id, claimToken);
		if (event == null) {
			return false;
		}
		int attempts = event.getAttempts() + 1;
		event.setAttempts(attempts);
		event.setLeaseUntil(null);
		event.setClaimToken(null);
		event.setLastError(limit(failure.getMessage(), 1000));
		if (attempts >= maxAttempts) {
			event.setStatus("DEAD");
		} else {
			event.setStatus("PENDING");
			event.setNextAttemptAt(LocalDateTime.now().plusMinutes(Math.min(60, 1L << Math.min(attempts, 6))));
		}
		return true;
	}

	/** 返回最终失败事件，供统一运维死信中心分页查看。 */
	@Transactional(readOnly = true)
	public Page<DomainEventOutbox> deadEvents(int page, int size) {
		return outbox.findByStatusOrderByCreateTimeDesc(
				"DEAD",
				PageRequest.of(
						Math.max(0, page),
						Math.min(Math.max(1, size), 100)));
	}

	/**
	 * 人工重试会清理租约和错误信息，让调度器立即重新领取事件。
	 * 只允许 DEAD 状态，避免管理员干扰正在投递的记录。
	 */
	@Transactional
	public DomainEventOutbox retry(String id) {
		DomainEventOutbox event = requireDead(id);
		event.setStatus("PENDING");
		event.setAttempts(0);
		event.setNextAttemptAt(LocalDateTime.now());
		event.setLeaseUntil(null);
		event.setClaimToken(null);
		event.setLastError(null);
		return outbox.save(event);
	}

	/** 忽略操作保留完整事件和失败原因，仅停止后续自动投递。 */
	@Transactional
	public DomainEventOutbox ignore(String id) {
		DomainEventOutbox event = requireDead(id);
		event.setStatus("IGNORED");
		event.setLeaseUntil(null);
		event.setClaimToken(null);
		return outbox.save(event);
	}

	/**
	 * 投递完成结果必须匹配当前租约令牌。租约过期后被重新领取的事件会获得新令牌，
	 * 原工作线程即使迟到返回，也不能覆盖新一轮投递结果。
	 */
	private DomainEventOutbox activeClaim(String id, String claimToken) {
		DomainEventOutbox event = outbox.findById(id).orElseThrow();
		if (!"PROCESSING".equals(event.getStatus())
				|| claimToken == null
				|| !claimToken.equals(event.getClaimToken())) {
			return null;
		}
		return event;
	}

	private DomainEventOutbox requireDead(String id) {
		DomainEventOutbox event = outbox.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("成绩领域死信不存在"));
		if (!"DEAD".equals(event.getStatus())) {
			throw new IllegalStateException("只有死信事件可以执行该操作");
		}
		return event;
	}

	@Transactional
	public DomainEventOutbox replay(String id) {
		DomainEventOutbox event = outbox.findById(id).orElseThrow();
		if ("SENT".equals(event.getStatus())) {
			throw new IllegalStateException("已成功投递的领域事件不可重放");
		}
		event.setStatus("PENDING");
		event.setNextAttemptAt(LocalDateTime.now());
		event.setLeaseUntil(null);
		event.setSentAt(null);
		event.setLastError(null);
		return event;
	}

	@Transactional
	public DomainEventOutbox markDead(String id, String reason) {
		DomainEventOutbox event = outbox.findById(id).orElseThrow();
		event.setStatus("DEAD");
		event.setLeaseUntil(null);
		event.setLastError(limit(reason, 1000));
		return event;
	}

	@Transactional(readOnly = true)
	public List<DomainEventOutbox> list(String status, int page, int size) {
		PageRequest request = PageRequest.of(page, size);
		return status == null || status.isBlank()
				? outbox.findAllByOrderByCreateTimeDesc(request)
				: outbox.findByStatusOrderByCreateTimeAsc(status, request);
	}

	@Transactional(readOnly = true)
	public long countByStatus(String status) {
		return outbox.countByStatus(status);
	}

	private String write(Object event) {
		try {
			return json.writeValueAsString(event);
		} catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("领域事件序列化失败", exception);
		}
	}

	private String limit(String message, int maxLength) {
		if (message == null) {
			return "unknown delivery failure";
		}
		return message.length() <= maxLength ? message : message.substring(0, maxLength);
	}
}
