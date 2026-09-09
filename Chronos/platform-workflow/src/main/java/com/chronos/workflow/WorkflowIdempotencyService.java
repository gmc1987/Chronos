package com.chronos.workflow;

import com.chronos.Idao.workflow.IWorkflowIdempotencyRepository;
import com.chronos.model.workflow.WorkflowIdempotency;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowIdempotencyService {
	private final IWorkflowIdempotencyRepository repository;

	public WorkflowIdempotencyService(IWorkflowIdempotencyRepository repository) {
		this.repository = repository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Reservation reserve(String actor, String operation, String key) {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("流程发起必须提供 idempotencyKey");
		}

		WorkflowIdempotency existing = repository
				.findByActorAndOperationAndIdempotencyKey(actor, operation, key.trim()).orElse(null);
		if (existing != null && "COMPLETED".equals(existing.getStatus())) {
			return new Reservation(existing.getId(), existing.getResourceId(), true);
		}
		if (existing != null && existing.getExpiresAt().isAfter(LocalDateTime.now())) {
			throw new IllegalStateException("相同请求正在处理中，请勿重复提交");
		}

		if (existing != null) {
			repository.delete(existing);
			repository.flush();
		}

		WorkflowIdempotency record = new WorkflowIdempotency();
		record.setActor(actor);
		record.setOperation(operation);
		record.setIdempotencyKey(key.trim());
		record.setExpiresAt(LocalDateTime.now().plusMinutes(10));
		try {
			record = repository.saveAndFlush(record);
		} catch (DataIntegrityViolationException exception) {
			throw new IllegalStateException("相同请求正在处理中，请勿重复提交", exception);
		}
		return new Reservation(record.getId(), null, false);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void complete(String reservationId, String resourceId) {
		WorkflowIdempotency record = repository.findById(reservationId).orElseThrow();
		record.setResourceId(resourceId);
		record.setStatus("COMPLETED");
		record.setExpiresAt(LocalDateTime.now().plusDays(7));
		repository.save(record);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void release(String reservationId) {
		repository.deleteById(reservationId);
	}

	public record Reservation(String id, String resourceId, boolean completed) {
	}
}
