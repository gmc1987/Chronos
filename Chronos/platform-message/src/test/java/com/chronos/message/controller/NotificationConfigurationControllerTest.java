package com.chronos.message.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.chronos.commons.model.PageView;
import com.chronos.message.Idao.IPublicationDeliveryRepository;
import com.chronos.message.model.NotificationTemplate;
import com.chronos.message.model.PublicationDelivery;
import com.chronos.message.service.impl.NotificationConfigurationService;
import com.chronos.message.service.impl.PublicationDeliveryService;

class NotificationConfigurationControllerTest {
	private NotificationConfigurationService service;
	private IPublicationDeliveryRepository deliveries;
	private NotificationConfigurationController controller;

	@BeforeEach
	void setUp() {
		service = mock(NotificationConfigurationService.class);
		deliveries = mock(IPublicationDeliveryRepository.class);
		controller = new NotificationConfigurationController(
				service,
				deliveries,
				mock(PublicationDeliveryService.class));
	}

	@Test
	void deadDeliveriesUseStablePageViewContract() {
		PublicationDelivery delivery = mock(PublicationDelivery.class);
		when(deliveries.findByStatus(eq("DEAD"), any()))
				.thenReturn(new PageImpl<>(
						List.of(delivery),
						PageRequest.of(0, 20),
						1));

		PageView<PublicationDelivery> page = controller.deadDeliveries(0, 20).getData();

		assertThat(page.content()).containsExactly(delivery);
		assertThat(page.totalElements()).isEqualTo(1);
		assertThat(page.number()).isZero();
		assertThat(page.first()).isTrue();
		assertThat(page.last()).isTrue();
	}

	@Test
	void templatesUseStablePageViewContract() {
		NotificationTemplate template = mock(NotificationTemplate.class);
		when(service.templates(eq("请假"), eq("IN_APP"), any()))
				.thenReturn(new PageImpl<>(
						List.of(template),
						PageRequest.of(1, 10),
						22));

		PageView<NotificationTemplate> page = controller
				.templates("请假", "IN_APP", 1, 10)
				.getData();

		assertThat(page.content()).containsExactly(template);
		assertThat(page.totalElements()).isEqualTo(22);
		assertThat(page.totalPages()).isEqualTo(3);
		assertThat(page.number()).isEqualTo(1);
		assertThat(page.first()).isFalse();
		assertThat(page.last()).isFalse();
	}
}
