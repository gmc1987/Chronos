package com.chronos.education.scheduling.model;

import com.chronos.commons.model.PageView;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageViewTest {
	@Test
	void exposesStablePaginationMetadata() {
		PageImpl<String> source = new PageImpl<>(
				List.of("first", "second"),
				PageRequest.of(1, 2),
				5);

		PageView<String> view = PageView.from(source);

		assertThat(view.content()).containsExactly("first", "second");
		assertThat(view.totalElements()).isEqualTo(5);
		assertThat(view.totalPages()).isEqualTo(3);
		assertThat(view.number()).isEqualTo(1);
		assertThat(view.size()).isEqualTo(2);
		assertThat(view.first()).isFalse();
		assertThat(view.last()).isFalse();
	}
}
