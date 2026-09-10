package com.chronos.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.Idao.form.IFormInstanceRepository;
import com.chronos.Idao.workflow.IWorkflowDefinitionAclRepository;
import com.chronos.Idao.workflow.IWorkflowDefinitionRepository;
import com.chronos.Idao.workflow.IWorkflowEdgeRepository;
import com.chronos.Idao.workflow.IWorkflowInstanceParticipantRepository;
import com.chronos.Idao.workflow.IWorkflowInstanceRepository;
import com.chronos.Idao.workflow.IWorkflowNodeRepository;
import com.chronos.Idao.workflow.IWorkflowTaskRepository;
import com.chronos.form.FormService;
import com.chronos.model.form.FormField;
import com.chronos.model.form.FormInstance;
import com.chronos.model.workflow.WorkflowDefinition;
import com.chronos.model.workflow.WorkflowInstance;
import com.chronos.model.workflow.WorkflowNode;
import com.chronos.model.workflow.WorkflowTask;
import com.chronos.service.iService.IDataScopeService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkflowSecurityServiceFilePermissionTest {
	private IWorkflowNodeRepository nodes;
	private WorkflowSecurityService security;

	@BeforeEach
	void setUp() {
		IWorkflowDefinitionRepository definitions = mock(IWorkflowDefinitionRepository.class);
		IWorkflowInstanceRepository instances = mock(IWorkflowInstanceRepository.class);
		IWorkflowTaskRepository tasks = mock(IWorkflowTaskRepository.class);
		IFormInstanceRepository formInstances = mock(IFormInstanceRepository.class);
		FormService forms = mock(FormService.class);
		nodes = mock(IWorkflowNodeRepository.class);

		WorkflowInstance instance = new WorkflowInstance();
		instance.setId("instance-1");
		instance.setDefinitionId("definition-1");
		instance.setCurrentNodeKey("approval");
		WorkflowDefinition definition = new WorkflowDefinition();
		definition.setId("definition-1");
		definition.setMainFormId("form-1");
		WorkflowTask task = new WorkflowTask();
		task.setStatus("PENDING");
		task.setAssignee("approver");
		FormInstance formInstance = new FormInstance();
		formInstance.setWorkflowInstanceId("instance-1");
		formInstance.setFormId("form-1");
		formInstance.setDataJson("{\"attachments\":[{\"id\":\"file-1\"}]}");
		FormField attachment = new FormField();
		attachment.setFormId("form-1");
		attachment.setFieldKey("attachments");
		attachment.setFieldType("FILE");

		when(instances.findById("instance-1")).thenReturn(Optional.of(instance));
		when(definitions.findById("definition-1")).thenReturn(Optional.of(definition));
		when(tasks.findByInstanceIdOrderByCreateTimeAsc("instance-1"))
				.thenReturn(List.of(task));
		when(formInstances.findByWorkflowInstanceIdOrderByCreateTimeAsc("instance-1"))
				.thenReturn(List.of(formInstance));
		when(forms.fields("form-1")).thenReturn(List.of(attachment));

		security = new WorkflowSecurityService(
				definitions,
				mock(IWorkflowDefinitionAclRepository.class),
				instances,
				mock(IWorkflowInstanceParticipantRepository.class),
				tasks,
				nodes,
				mock(IWorkflowEdgeRepository.class),
				mock(IAdminUserRepository.class),
				mock(IEmployeeAssignmentRepository.class),
				mock(IDataScopeService.class),
				mock(WorkflowAssigneeResolver.class),
				formInstances,
				forms);
	}

	@Test
	void readOnlyAttachmentCannotBeUploadedOrDeletedByCurrentApprover() {
		when(nodes.findByFlowIdAndNodeKey("definition-1", "approval"))
				.thenReturn(Optional.of(node("{\"permissions\":{}}")));

		assertThat(security.canEditAnyFileField("approver", "instance-1")).isFalse();
		assertThat(security.canEditFile("approver", "instance-1", "file-1")).isFalse();
	}

	@Test
	void editPermissionOnlyAllowsTheFileReferencedByThatField() {
		when(nodes.findByFlowIdAndNodeKey("definition-1", "approval"))
				.thenReturn(Optional.of(node(
						"{\"permissions\":{\"form-1.attachments\":\"EDIT\"}}")));

		assertThat(security.canEditAnyFileField("approver", "instance-1")).isTrue();
		assertThat(security.canEditFile("approver", "instance-1", "file-1")).isTrue();
		assertThat(security.canEditFile("approver", "instance-1", "file-other")).isFalse();
	}

	private WorkflowNode node(String permissions) {
		WorkflowNode node = new WorkflowNode();
		node.setFieldPermissionsJson(permissions);
		node.setAdditionalFormIds("[]");
		return node;
	}
}
