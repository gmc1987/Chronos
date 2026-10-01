package com.chronos.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chronos.Idao.form.IFormDefinitionRepository;
import com.chronos.Idao.form.IFormInstanceRepository;
import com.chronos.Idao.form.IFormInstanceRevisionRepository;
import com.chronos.Idao.workflow.IWorkflowDefinitionRepository;
import com.chronos.Idao.workflow.IWorkflowEdgeRepository;
import com.chronos.Idao.workflow.IWorkflowExecutionLogRepository;
import com.chronos.Idao.workflow.IWorkflowIncidentRepository;
import com.chronos.Idao.workflow.IWorkflowInstanceParticipantRepository;
import com.chronos.Idao.workflow.IWorkflowInstanceRepository;
import com.chronos.Idao.workflow.IWorkflowNodeRepository;
import com.chronos.Idao.workflow.IWorkflowTaskRepository;
import com.chronos.form.FormService;
import com.chronos.model.form.FormInstanceRevision;
import com.chronos.model.workflow.WorkflowDefinition;
import com.chronos.model.workflow.WorkflowInstance;
import com.chronos.service.iService.IAuditLogService;

@ExtendWith(MockitoExtension.class)
class WorkflowArchiveServiceTest {
	@Mock private IWorkflowInstanceRepository instances;
	@Mock private IWorkflowDefinitionRepository definitions;
	@Mock private IWorkflowNodeRepository nodes;
	@Mock private IWorkflowEdgeRepository edges;
	@Mock private IWorkflowTaskRepository tasks;
	@Mock private IFormInstanceRepository formInstances;
	@Mock private IFormInstanceRevisionRepository formRevisions;
	@Mock private IFormDefinitionRepository formDefinitions;
	@Mock private FormService forms;
	@Mock private IWorkflowExecutionLogRepository executionLogs;
	@Mock private IWorkflowIncidentRepository incidents;
	@Mock private IWorkflowInstanceParticipantRepository participants;
	@Mock private RuntimeService runtime;
	@Mock private IAuditLogService audit;
	@InjectMocks private WorkflowArchiveService service;

	@Test
	void exportsConsistentTerminalPackageWithChecksum() {
		WorkflowInstance instance = instance("COMPLETED");
		WorkflowDefinition definition = new WorkflowDefinition();
		definition.setId("definition-1");
		definition.setFlowName("Leave approval");
		when(instances.findById("instance-1")).thenReturn(Optional.of(instance));
		when(definitions.findById("definition-1")).thenReturn(Optional.of(definition));
		when(nodes.findByFlowIdOrderByCreateTimeAsc("definition-1")).thenReturn(List.of());
		when(tasks.findByInstanceIdOrderByCreateTimeAsc("instance-1")).thenReturn(List.of());
		when(incidents.findByInstanceIdOrderByCreateTimeAsc("instance-1")).thenReturn(List.of());
		when(formInstances.findByWorkflowInstanceIdOrderByCreateTimeAsc("instance-1"))
				.thenReturn(List.of());
		FormInstanceRevision revision = new FormInstanceRevision();
		revision.setRevisionNo(1);
		revision.setDataJson("{\"reason\":\"original\"}");
		when(formRevisions.findByWorkflowInstanceIdOrderByCreateTimeAsc("instance-1"))
				.thenReturn(List.of(revision));
		when(edges.findByFlowIdOrderByCreateTimeAsc("definition-1")).thenReturn(List.of());
		when(participants.findByInstanceIdOrderByCreateTimeAsc("instance-1"))
				.thenReturn(List.of());
		when(executionLogs.findByInstanceIdOrderByCreateTimeAsc("instance-1"))
				.thenReturn(List.of());

		var archive = service.export("instance-1", "admin");

		assertThat(archive.sha256()).hasSize(64);
		assertThat(archive.payloadJson()).contains(
				"instance-1", "Leave approval", "formRevisions", "original");
	}

	@Test
	void neverExportsRunningInstance() {
		when(instances.findById("instance-1"))
				.thenReturn(Optional.of(instance("RUNNING")));

		assertThatThrownBy(() -> service.export("instance-1", "admin"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private WorkflowInstance instance(String status) {
		WorkflowInstance instance = new WorkflowInstance();
		instance.setId("instance-1");
		instance.setDefinitionId("definition-1");
		instance.setDefinitionVersion("v1");
		instance.setInitiator("teacher");
		instance.setStatus(status);
		return instance;
	}
}
