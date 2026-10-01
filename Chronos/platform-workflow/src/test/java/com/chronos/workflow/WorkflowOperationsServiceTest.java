package com.chronos.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.chronos.Idao.workflow.IWorkflowIncidentRepository;
import com.chronos.Idao.workflow.IWorkflowOutboxRepository;
import com.chronos.Idao.workflow.IWorkflowTaskRepository;

class WorkflowOperationsServiceTest {
	@Test
	void alertsWhenDeadLettersOrIncidentsExist() {
		IWorkflowTaskRepository tasks = mock(IWorkflowTaskRepository.class);
		IWorkflowIncidentRepository incidents = mock(IWorkflowIncidentRepository.class);
		IWorkflowOutboxRepository outbox = mock(IWorkflowOutboxRepository.class);
		when(tasks.countByStatusInAndCreateTimeBefore(anyList(), any(LocalDateTime.class)))
				.thenReturn(2L);
		when(tasks.countByStatusInAndDueAtBefore(anyList(), any(LocalDateTime.class)))
				.thenReturn(1L);
		when(incidents.countByStatus("OPEN")).thenReturn(1L);
		when(outbox.countByStatus("PENDING")).thenReturn(3L);
		when(outbox.countByStatus("DEAD")).thenReturn(1L);

		var health = new WorkflowOperationsService(tasks, incidents, outbox, 20, 0, 24)
				.health();

		assertThat(health.alert()).isTrue();
		assertThat(health.oldTasks()).isEqualTo(2);
		assertThat(health.overdueTasks()).isEqualTo(1);
		assertThat(health.deadEvents()).isEqualTo(1);
	}
}
