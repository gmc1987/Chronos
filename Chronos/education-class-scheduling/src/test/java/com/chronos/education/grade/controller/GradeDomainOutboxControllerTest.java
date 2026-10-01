package com.chronos.education.grade.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.commons.model.PageView;
import com.chronos.education.grade.model.DomainEventOutbox;
import com.chronos.education.grade.service.DomainEventOutboxService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class GradeDomainOutboxControllerTest {
	@Test
	void deadEventsUseStablePageViewContract() {
		DomainEventOutboxService service = mock(DomainEventOutboxService.class);
		DomainEventOutbox event = new DomainEventOutbox();
		event.setId("event-1");
		when(service.deadEvents(1, 10)).thenReturn(new PageImpl<>(
				List.of(event),
				PageRequest.of(1, 10),
				21));
		GradeDomainOutboxController controller = new GradeDomainOutboxController(service);

		PageView<DomainEventOutbox> page = controller.dead(1, 10).getData();

		assertThat(page.content()).containsExactly(event);
		assertThat(page.totalElements()).isEqualTo(21);
		assertThat(page.totalPages()).isEqualTo(3);
		assertThat(page.number()).isEqualTo(1);
		assertThat(page.first()).isFalse();
		assertThat(page.last()).isFalse();
	}
}
