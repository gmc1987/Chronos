package com.chronos.education.scheduling.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.commons.model.PageView;
import com.chronos.education.scheduling.model.EducationDomainOutbox;
import com.chronos.education.scheduling.service.EducationDomainEventService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class EducationDomainOutboxControllerTest {
	@Test
	void deadEventsUseStablePageViewContract() {
		EducationDomainEventService service = mock(EducationDomainEventService.class);
		EducationDomainOutbox event = new EducationDomainOutbox();
		event.setId("event-1");
		when(service.deadEvents(0, 20)).thenReturn(new PageImpl<>(
				List.of(event),
				PageRequest.of(0, 20),
				1));
		EducationDomainOutboxController controller = new EducationDomainOutboxController(service);

		PageView<EducationDomainOutbox> page = controller.dead(0, 20).getData();

		assertThat(page.content()).containsExactly(event);
		assertThat(page.totalElements()).isEqualTo(1);
		assertThat(page.number()).isZero();
		assertThat(page.first()).isTrue();
		assertThat(page.last()).isTrue();
	}
}
