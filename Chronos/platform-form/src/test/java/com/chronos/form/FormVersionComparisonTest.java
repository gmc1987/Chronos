package com.chronos.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.chronos.Idao.form.IFormDefinitionRepository;
import com.chronos.Idao.form.IFormFieldRepository;
import com.chronos.Idao.form.IFormInstanceRepository;
import com.chronos.Idao.form.IFormInstanceRevisionRepository;
import com.chronos.model.form.FormDefinition;
import com.chronos.model.form.FormField;

class FormVersionComparisonTest {
	@Test
	void reportsBreakingChangesWithoutMutatingPublishedVersions() {
		IFormDefinitionRepository definitions = mock(IFormDefinitionRepository.class);
		IFormFieldRepository fields = mock(IFormFieldRepository.class);
		FormDefinition oldForm = form("old", "LEAVE", "v1");
		FormDefinition newForm = form("new", "LEAVE", "v2");
		when(definitions.findById("old")).thenReturn(Optional.of(oldForm));
		when(definitions.findById("new")).thenReturn(Optional.of(newForm));
		when(fields.findByFormIdOrderBySortOrderAscCreateTimeAsc("old"))
				.thenReturn(List.of(field("days", "NUMBER", false), field("reason", "TEXT", false)));
		when(fields.findByFormIdOrderBySortOrderAscCreateTimeAsc("new"))
				.thenReturn(List.of(field("days", "TEXT", true), field("attachment", "FILE", false)));

		FormService service = new FormService(
				definitions,
				fields,
				mock(IFormInstanceRepository.class),
				mock(IFormInstanceRevisionRepository.class));
		var comparison = service.compareVersions("old", "new");

		assertThat(comparison.requiresManualMigration()).isTrue();
		assertThat(comparison.changes())
				.extracting(FormService.FormFieldChange::change)
				.contains("TYPE_CHANGED", "REQUIRED_ADDED", "REMOVED", "ADDED");
		assertThat(oldForm.getVersion()).isEqualTo("v1");
	}

	@Test
	void rejectsComparingUnrelatedForms() {
		IFormDefinitionRepository definitions = mock(IFormDefinitionRepository.class);
		when(definitions.findById("one"))
				.thenReturn(Optional.of(form("one", "LEAVE", "v1")));
		when(definitions.findById("two"))
				.thenReturn(Optional.of(form("two", "PURCHASE", "v2")));

		FormService service = new FormService(
				definitions,
				mock(IFormFieldRepository.class),
				mock(IFormInstanceRepository.class),
				mock(IFormInstanceRevisionRepository.class));
		assertThatThrownBy(() -> service.compareVersions("one", "two"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private FormDefinition form(String id, String key, String version) {
		FormDefinition form = new FormDefinition();
		form.setId(id);
		form.setFormKey(key);
		form.setVersion(version);
		form.setStatus("PUBLISHED");
		return form;
	}

	private FormField field(String key, String type, boolean required) {
		FormField field = new FormField();
		field.setFieldKey(key);
		field.setFieldLabel(key);
		field.setFieldType(type);
		field.setRequired(required);
		field.setSortOrder(0);
		field.setOptionsJson("[]");
		return field;
	}
}
