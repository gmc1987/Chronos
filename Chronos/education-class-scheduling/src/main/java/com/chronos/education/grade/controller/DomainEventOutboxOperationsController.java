package com.chronos.education.grade.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.grade.model.DomainEventOutbox;
import com.chronos.education.grade.service.DomainEventOutboxService;
import com.chronos.service.iService.IAuditLogService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/admin/education/domain-events/outbox")
@PreAuthorize("@iamAuthorization.has(authentication,'education:domain-event:manage')")
public class DomainEventOutboxOperationsController {
	private final DomainEventOutboxService service;
	private final IAuditLogService audit;

	public DomainEventOutboxOperationsController(DomainEventOutboxService service, IAuditLogService audit) {
		this.service = service;
		this.audit = audit;
	}

	@GetMapping
	public ResultData<List<OutboxSummary>> list(
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		if (page < 0 || size < 1 || size > 200) {
				throw new ResponseStatusException(
						BAD_REQUEST, "page must be non-negative and size must be between 1 and 200");
		}
		return ok(service.list(status, page, size).stream().map(OutboxSummary::from).toList());
	}

	@PostMapping("/{id}/replay")
	public ResultData<OutboxSummary> replay(@PathVariable String id, Authentication authentication) {
		DomainEventOutbox event = service.replay(id);
		audit.log(authentication.getName(), "EDU_DOMAIN_EVENT_OUTBOX_REPLAY", "eventId=" + id);
		return ok(OutboxSummary.from(event));
	}

	@PostMapping("/{id}/dead")
	public ResultData<OutboxSummary> dead(
			@PathVariable String id,
			@RequestParam(defaultValue = "manually marked dead") String reason,
			Authentication authentication) {
		DomainEventOutbox event = service.markDead(id, reason);
		audit.log(authentication.getName(), "EDU_DOMAIN_EVENT_OUTBOX_DEAD", "eventId=" + id);
		return ok(OutboxSummary.from(event));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}

	public record OutboxSummary(
			String id,
			String eventType,
			String aggregateId,
			String status,
			int attempts,
			java.time.LocalDateTime nextAttemptAt,
			java.time.LocalDateTime leaseUntil,
			java.time.LocalDateTime sentAt,
			String lastError,
			String deduplicationKey) {
		static OutboxSummary from(DomainEventOutbox event) {
			return new OutboxSummary(
					event.getId(),
					event.getEventType(),
					event.getAggregateId(),
					event.getStatus(),
					event.getAttempts(),
					event.getNextAttemptAt(),
					event.getLeaseUntil(),
					event.getSentAt(),
					event.getLastError(),
					event.getDeduplicationKey());
		}
	}
}
