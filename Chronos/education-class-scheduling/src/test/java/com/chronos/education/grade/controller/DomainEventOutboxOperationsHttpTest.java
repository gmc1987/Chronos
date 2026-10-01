package com.chronos.education.grade.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chronos.education.grade.model.DomainEventOutbox;
import com.chronos.education.grade.service.DomainEventOutboxService;
import com.chronos.service.iService.IAuditLogService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DomainEventOutboxOperationsHttpTest {
	private final DomainEventOutboxService service = mock(DomainEventOutboxService.class);
	private final IAuditLogService audit = mock(IAuditLogService.class);
	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
			new DomainEventOutboxOperationsController(service, audit))
			.build();

	@Test
	void listReturnsOperationalMetadataButNotPayload() throws Exception {
		DomainEventOutbox event = event("outbox-1", "DEAD");
		event.setPayloadJson("{\"secret\":\"not exposed\"}");
		when(service.list("DEAD", 0, 50)).thenReturn(List.of(event));

		mockMvc.perform(get("/admin/education/domain-events/outbox")
						.param("status", "DEAD")
						.principal(new UsernamePasswordAuthenticationToken("operator", "n/a")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].id").value("outbox-1"))
				.andExpect(jsonPath("$.data[0].status").value("DEAD"))
				.andExpect(jsonPath("$.data[0].payloadJson").doesNotExist());
	}

	@Test
	void replayAuditsOperatorAndReturnsPendingState() throws Exception {
		DomainEventOutbox event = event("outbox-1", "PENDING");
		when(service.replay("outbox-1")).thenReturn(event);

		mockMvc.perform(post("/admin/education/domain-events/outbox/outbox-1/replay")
						.principal(new UsernamePasswordAuthenticationToken("operator", "n/a")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PENDING"));

		verify(audit).log("operator", "EDU_DOMAIN_EVENT_OUTBOX_REPLAY", "eventId=outbox-1");
	}

	@Test
	void invalidPageSizeIsRejected() throws Exception {
		mockMvc.perform(get("/admin/education/domain-events/outbox").param("size", "201"))
				.andExpect(status().isBadRequest());
	}

	private DomainEventOutbox event(String id, String status) {
		DomainEventOutbox event = new DomainEventOutbox();
		event.setId(id);
		event.setEventType("CourseGradesPublishedV1");
		event.setAggregateId("gradebook-1");
		event.setStatus(status);
		event.setAttempts(2);
		event.setDeduplicationKey("event-1");
		return event;
	}
}
