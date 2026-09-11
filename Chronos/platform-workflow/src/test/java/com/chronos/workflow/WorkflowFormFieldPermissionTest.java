package com.chronos.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.Idao.form.IFormDefinitionRepository;
import com.chronos.Idao.form.IFormFieldRepository;
import com.chronos.Idao.form.IFormInstanceRepository;
import com.chronos.form.FormService;
import com.chronos.model.form.FormDefinition;
import com.chronos.model.form.FormField;
import com.chronos.model.form.FormInstance;
import com.chronos.model.workflow.WorkflowNode;
import com.chronos.model.workflow.WorkflowTask;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowFormFieldPermissionTest {

	@Test
	void starterReworkOnlyOffersResubmitOperation() {
		WorkflowService service = mock(WorkflowService.class);
		when(service.taskOperationView(any(), any()))
				.thenCallRealMethod();
		WorkflowNode node = new WorkflowNode();
		node.setPropertiesJson("{}");
		WorkflowTask task = new WorkflowTask();
		task.setTaskKind("STARTER_REWORK");

		Map<String, Boolean> operations = service.taskOperationView(node, task);

		assertThat(operations.get("resubmit")).isTrue();
		assertThat(operations)
				.containsEntry("approve", false)
				.containsEntry("reject", false)
				.containsEntry("return", false)
				.containsEntry("transfer", false)
				.containsEntry("addSign", false)
				.containsEntry("cc", false);
	}

	@Test
	void runtimeSaveOnlyAcceptsExplicitEditFields() {
		IFormDefinitionRepository definitions = mock(IFormDefinitionRepository.class);
		IFormFieldRepository fields = mock(IFormFieldRepository.class);
		IFormInstanceRepository instances = mock(IFormInstanceRepository.class);
		FormDefinition definition = publishedForm();
		FormInstance stored = storedForm();
		when(definitions.findById("form-1")).thenReturn(Optional.of(definition));
		when(fields.findByFormIdOrderBySortOrderAscCreateTimeAsc("form-1"))
				.thenReturn(schema());
		when(instances.findByWorkflowInstanceIdAndFormIdAndNodeKey(
				"instance-1",
				"form-1",
				"approval"))
				.thenReturn(Optional.of(stored));
		when(instances.save(any(FormInstance.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		FormService service = new FormService(definitions, fields, instances);
		Map<String, String> permissions = Map.of(
				"form-1.readField", "READ",
				"form-1.editField", "EDIT",
				"form-1.hiddenField", "HIDDEN");

		assertThatThrownBy(() -> service.saveRuntime(
				"instance-1",
				"form-1",
				"approval",
				"MAIN",
				"approver",
				Map.of("readField", "changed"),
				permissions,
				Set.of(),
				true))
				.hasMessageContaining("字段无编辑权限：readField");
		assertThatThrownBy(() -> service.saveRuntime(
				"instance-1",
				"form-1",
				"approval",
				"MAIN",
				"approver",
				Map.of("hiddenField", "changed"),
				permissions,
				Set.of(),
				true))
				.hasMessageContaining("字段无编辑权限：hiddenField");

		FormInstance saved = service.saveRuntime(
				"instance-1",
				"form-1",
				"approval",
				"MAIN",
				"approver",
				Map.of("editField", "changed"),
				permissions,
				Set.of(),
				true);
		assertThat(saved.getDataJson()).contains("\"editField\":\"changed\"");
		assertThat(saved.getDataJson()).contains("\"readField\":\"original\"");
	}

	@Test
	void hiddenFieldValueIsRemovedFromRuntimeResponseData() {
		WorkflowService service = mock(WorkflowService.class);
		when(service.visibleRuntimeData(any(), any(), any(), any()))
				.thenCallRealMethod();
		Map<String, Object> visible = service.visibleRuntimeData(
				"form-1",
				schema(),
				Map.of(
						"form-1.readField", "READ",
						"form-1.editField", "EDIT",
						"form-1.hiddenField", "HIDDEN"),
				Map.of(
						"readField", "read-value",
						"editField", "edit-value",
						"hiddenField", "secret",
						"unknownField", "legacy"));

		assertThat(visible).containsOnly(
				org.assertj.core.data.MapEntry.entry("readField", "read-value"),
				org.assertj.core.data.MapEntry.entry("editField", "edit-value"));
	}

	@Test
	void requiredFlagMatchesRuntimeSubmitValidation() {
		WorkflowService service = mock(WorkflowService.class);
		when(service.isRuntimeFieldRequired(any(), any(), any(), any()))
				.thenCallRealMethod();
		FormField requiredField = field("requiredField");
		requiredField.setRequired(true);

		assertThat(service.isRuntimeFieldRequired(
				requiredField,
				"form-1.requiredField",
				"EDIT",
				Set.of()))
				.isTrue();
		assertThat(service.isRuntimeFieldRequired(
				requiredField,
				"form-1.requiredField",
				"READ",
				Set.of()))
				.isFalse();
		assertThat(service.isRuntimeFieldRequired(
				field("optionalField"),
				"form-1.optionalField",
				"EDIT",
				Set.of("form-1.optionalField")))
				.isTrue();
	}

	@Test
	void approvalValidationRejectsMissingOrDraftRequiredAdditionalForm() {
		IFormDefinitionRepository definitions = mock(IFormDefinitionRepository.class);
		IFormFieldRepository fields = mock(IFormFieldRepository.class);
		IFormInstanceRepository instances = mock(IFormInstanceRepository.class);
		FormDefinition definition = publishedForm();
		FormField requiredField = field("reviewOpinion");
		requiredField.setRequired(true);
		when(definitions.findById("form-1")).thenReturn(Optional.of(definition));
		when(fields.findByFormIdOrderBySortOrderAscCreateTimeAsc("form-1"))
				.thenReturn(List.of(requiredField));
		when(instances.findByWorkflowInstanceIdAndFormIdAndNodeKey(
				"instance-1",
				"form-1",
				"approval"))
				.thenReturn(Optional.empty());
		FormService service = new FormService(definitions, fields, instances);

		assertThatThrownBy(() -> service.validateRuntimeRequiredFields(
				"instance-1",
				"form-1",
				"approval",
				Map.of("form-1.reviewOpinion", "EDIT"),
				Set.of("form-1.reviewOpinion")))
				.hasMessageContaining("请先填写并提交节点表单");

		FormInstance draft = storedForm();
		draft.setStatus("DRAFT");
		when(instances.findByWorkflowInstanceIdAndFormIdAndNodeKey(
				"instance-1",
				"form-1",
				"approval"))
				.thenReturn(Optional.of(draft));
		assertThatThrownBy(() -> service.validateRuntimeRequiredFields(
				"instance-1",
				"form-1",
				"approval",
				Map.of("form-1.reviewOpinion", "EDIT"),
				Set.of("form-1.reviewOpinion")))
				.hasMessageContaining("请先提交节点表单");
	}

	@Test
	void approvalValidationAcceptsSubmittedRequiredAdditionalForm() {
		IFormDefinitionRepository definitions = mock(IFormDefinitionRepository.class);
		IFormFieldRepository fields = mock(IFormFieldRepository.class);
		IFormInstanceRepository instances = mock(IFormInstanceRepository.class);
		FormDefinition definition = publishedForm();
		FormField requiredField = field("reviewOpinion");
		requiredField.setRequired(true);
		FormInstance submitted = new FormInstance();
		submitted.setStatus("SUBMITTED");
		submitted.setDataJson("{\"reviewOpinion\":\"核验通过\"}");
		when(definitions.findById("form-1")).thenReturn(Optional.of(definition));
		when(fields.findByFormIdOrderBySortOrderAscCreateTimeAsc("form-1"))
				.thenReturn(List.of(requiredField));
		when(instances.findByWorkflowInstanceIdAndFormIdAndNodeKey(
				"instance-1",
				"form-1",
				"approval"))
				.thenReturn(Optional.of(submitted));
		FormService service = new FormService(definitions, fields, instances);

		service.validateRuntimeRequiredFields(
				"instance-1",
				"form-1",
				"approval",
				Map.of("form-1.reviewOpinion", "EDIT"),
				Set.of("form-1.reviewOpinion"));
	}

	private FormDefinition publishedForm() {
		FormDefinition definition = new FormDefinition();
		definition.setId("form-1");
		definition.setStatus("PUBLISHED");
		return definition;
	}

	private FormInstance storedForm() {
		FormInstance instance = new FormInstance();
		instance.setDataJson(
				"{\"readField\":\"original\",\"editField\":\"old\",\"hiddenField\":\"secret\"}");
		return instance;
	}

	private List<FormField> schema() {
		return List.of(
				field("readField"),
				field("editField"),
				field("hiddenField"));
	}

	private FormField field(String key) {
		FormField field = new FormField();
		field.setFormId("form-1");
		field.setFieldKey(key);
		return field;
	}
}
