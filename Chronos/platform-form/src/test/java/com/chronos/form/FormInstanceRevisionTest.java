package com.chronos.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chronos.Idao.form.IFormDefinitionRepository;
import com.chronos.Idao.form.IFormFieldRepository;
import com.chronos.Idao.form.IFormInstanceRepository;
import com.chronos.Idao.form.IFormInstanceRevisionRepository;
import com.chronos.model.form.FormDefinition;
import com.chronos.model.form.FormField;
import com.chronos.model.form.FormInstance;
import com.chronos.model.form.FormInstanceRevision;

class FormInstanceRevisionTest {
    @Test
    void preservesLegacySnapshotBeforeFirstChange() {
        IFormDefinitionRepository definitions = mock(IFormDefinitionRepository.class);
        IFormFieldRepository fields = mock(IFormFieldRepository.class);
        IFormInstanceRepository instances = mock(IFormInstanceRepository.class);
        IFormInstanceRevisionRepository revisions = mock(IFormInstanceRevisionRepository.class);
        FormDefinition definition = new FormDefinition();
        definition.setStatus("PUBLISHED");
        FormField field = new FormField();
        field.setFieldKey("reason");
        FormInstance existing = new FormInstance();
        existing.setId("form-instance-1");
        existing.setOwner("starter");
        existing.setStatus("SUBMITTED");
        existing.setDataJson("{\"reason\":\"before\"}");

        when(definitions.findById("form-1")).thenReturn(Optional.of(definition));
        when(fields.findByFormIdOrderBySortOrderAscCreateTimeAsc("form-1"))
                .thenReturn(List.of(field));
        when(instances.findLockedForUpdate("instance-1", "form-1", "approval"))
                .thenReturn(Optional.of(existing));
        when(instances.save(any(FormInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(revisions.countByFormInstanceId("form-instance-1")).thenReturn(0L);

        FormService service = new FormService(definitions, fields, instances, revisions);
        service.saveRuntime(
                "instance-1", "form-1", "approval", "MAIN", "reviewer",
                Map.of("reason", "after"), Map.of("form-1.reason", "EDIT"),
                Set.of(), false);

        ArgumentCaptor<FormInstanceRevision> saved = ArgumentCaptor.forClass(FormInstanceRevision.class);
        verify(revisions, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(FormInstanceRevision::getRevisionNo)
                .containsExactly(1, 2);
        assertThat(saved.getAllValues().get(0).getDataJson()).contains("before");
        assertThat(saved.getAllValues().get(1).getDataJson()).contains("after");
    }

    @Test
    void unchangedSubmissionDoesNotCreateAnotherRevision() {
        IFormDefinitionRepository definitions = mock(IFormDefinitionRepository.class);
        IFormFieldRepository fields = mock(IFormFieldRepository.class);
        IFormInstanceRepository instances = mock(IFormInstanceRepository.class);
        IFormInstanceRevisionRepository revisions = mock(IFormInstanceRevisionRepository.class);
        FormDefinition definition = new FormDefinition();
        definition.setStatus("PUBLISHED");
        FormField field = new FormField();
        field.setFieldKey("reason");
        FormInstance existing = new FormInstance();
        existing.setId("form-instance-1");
        existing.setOwner("reviewer");
        existing.setStatus("SUBMITTED");
        existing.setDataJson("{\"reason\":\"after\"}");

        when(definitions.findById("form-1")).thenReturn(Optional.of(definition));
        when(fields.findByFormIdOrderBySortOrderAscCreateTimeAsc("form-1"))
                .thenReturn(List.of(field));
        when(instances.findLockedForUpdate("instance-1", "form-1", "approval"))
                .thenReturn(Optional.of(existing));

        FormService service = new FormService(definitions, fields, instances, revisions);
        service.saveRuntime(
                "instance-1", "form-1", "approval", "MAIN", "reviewer",
                Map.of("reason", "after"), Map.of("form-1.reason", "EDIT"),
                Set.of(), false);

        verify(instances, times(0)).save(any(FormInstance.class));
        verify(revisions, times(0)).save(any(FormInstanceRevision.class));
    }
}
