package com.chronos.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.chronos.commons.model.PageView;
import com.chronos.model.form.FormDefinition;

class FormControllerTest {
	@Test
	void listUsesStablePageViewAndNormalizesPagination() {
		FormService service = mock(FormService.class);
		FormDefinition form = mock(FormDefinition.class);
		when(service.list(any())).thenReturn(new PageImpl<>(
				List.of(form),
				PageRequest.of(0, 1),
				2));

		PageView<FormDefinition> page = new FormController(service)
				.list(-1, 0)
				.getData();

		assertThat(page.content()).containsExactly(form);
		assertThat(page.totalElements()).isEqualTo(2);
		assertThat(page.totalPages()).isEqualTo(2);
		assertThat(page.number()).isZero();
		assertThat(page.size()).isEqualTo(1);
	}
}
