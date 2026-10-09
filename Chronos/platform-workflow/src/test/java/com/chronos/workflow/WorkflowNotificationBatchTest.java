package com.chronos.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chronos.Idao.workflow.IWorkflowOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkflowNotificationBatchTest {
	@Test
	void largeAudienceKeepsOnePayloadPerUserAcrossBatchBoundary() throws Exception {
		IWorkflowOutboxRepository outbox = mock(IWorkflowOutboxRepository.class);
		WorkflowNotificationService service = service(outbox);
		List<String> recipients = new ArrayList<>();
		for (int index = 0; index < 201; index++) {
			recipients.add("student-" + index);
		}
		recipients.add("student-0");
		recipients.add(null);
		recipients.add(" ");
		service.enqueueUserEvents("PUBLISHED", "version-1", recipients,
				"课表\"发布", "含换行\n的通知", "V1");

		ArgumentCaptor<String> payloads = ArgumentCaptor.forClass(String.class);
		verify(outbox, org.mockito.Mockito.times(2)).insertUserEventBatch(
				eq("PUBLISHED"), eq("version-1"), payloads.capture());
		ObjectMapper json = new ObjectMapper();
		List<String> actualUsers = new ArrayList<>();
		for (String batch : payloads.getAllValues()) {
			for (var row : json.readTree(batch)) {
				var payload = json.readTree(row.get("payload_json").asText());
				String recipient = payload.get("recipient").asText();
				actualUsers.add(recipient);
				assertThat(payload.get("title").asText()).isEqualTo("课表\"发布");
				assertThat(payload.get("content").asText()).isEqualTo("含换行\n的通知");
				assertThat(row.get("deduplication_key").asText())
						.isEqualTo("PUBLISHED:version-1:V1:" + recipient);
			}
		}
		assertThat(actualUsers).hasSize(201).doesNotHaveDuplicates();
		assertThat(actualUsers).contains("student-0", "student-200");
	}

	@Test
	void noAudienceDoesNotWriteAnEmptyBatch() {
		IWorkflowOutboxRepository outbox = mock(IWorkflowOutboxRepository.class);
		WorkflowNotificationService service = service(outbox);
		service.enqueueUserEvents("PUBLISHED", "version-1", List.of(" "), "title", "content", "V1");
		verifyNoInteractions(outbox);
	}

	private WorkflowNotificationService service(IWorkflowOutboxRepository outbox) {
		return new WorkflowNotificationService(outbox, null, null, null, null, null);
	}
}
