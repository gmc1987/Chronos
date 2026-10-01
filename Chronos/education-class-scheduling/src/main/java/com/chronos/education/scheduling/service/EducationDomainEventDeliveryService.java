package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.model.EducationDomainOutbox;
import com.chronos.education.scheduling.model.dto.ResearchErrorDtos.WrongAnswerConfirmed;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在独立事务中投递单条教育领域事件。
 *
 * <p>事件消费失败时，消费事务可以正常回滚，但不会把外层 Outbox 状态事务
 * 标记为 rollback-only。外层事务因此仍能持久化重试次数、错误原因和死信状态。</p>
 */
@Service
public class EducationDomainEventDeliveryService {
	private static final String WRONG_ANSWER_CONFIRMED = "WrongAnswerConfirmed";

	private final ResearchErrorService errorRecords;
	private final ObjectMapper json;
	private final EducationGradeEventConsumer gradeEvents;

	public EducationDomainEventDeliveryService(
			ResearchErrorService errorRecords,
			ObjectMapper json,
			EducationGradeEventConsumer gradeEvents) {
		this.errorRecords = errorRecords;
		this.json = json;
		this.gradeEvents = gradeEvents;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void deliver(EducationDomainOutbox event) throws Exception {
		if (!WRONG_ANSWER_CONFIRMED.equals(event.getEventType())) {
			if (gradeEvents == null) {
				throw new IllegalArgumentException(
						"不支持的教育领域事件：" + event.getEventType());
			}
			gradeEvents.consume(event.getEventType(), event.getPayloadJson());
			return;
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
}
