package com.chronos.message.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.chronos.commons.model.PageView;
import com.chronos.message.model.PublicationView;
import com.chronos.message.service.iService.IPublicationService;

class PublicationControllerTest {
	private IPublicationService service;
	private PublicationController controller;
	private Principal principal;

	@BeforeEach
	void setUp() {
		service = mock(IPublicationService.class);
		controller = new PublicationController(service);
		principal = () -> "teacher.demo";
	}

	@Test
	void adminListUsesStablePageViewContract() {
		PublicationView publication = mock(PublicationView.class);
		when(service.adminList(eq("NOTICE"), eq("PUBLISHED"), eq("课表"), eq("teacher.demo"), any()))
				.thenReturn(new PageImpl<>(
						List.of(publication),
						PageRequest.of(1, 10),
						21));

		PageView<PublicationView> page = controller
				.list("NOTICE", "PUBLISHED", "课表", 1, 10, principal)
				.getData();

		assertThat(page.content()).containsExactly(publication);
		assertThat(page.totalElements()).isEqualTo(21);
		assertThat(page.totalPages()).isEqualTo(3);
		assertThat(page.number()).isEqualTo(1);
		assertThat(page.size()).isEqualTo(10);
		assertThat(page.first()).isFalse();
		assertThat(page.last()).isFalse();
	}

	@Test
	void portalListUsesStablePageViewContract() {
		PublicationView publication = mock(PublicationView.class);
		when(service.visible(eq("teacher.demo"), eq("ANNOUNCEMENT"), eq("校园"), eq(true), any()))
				.thenReturn(new PageImpl<>(
						List.of(publication),
						PageRequest.of(0, 20),
						1));

		PageView<PublicationView> page = controller
				.visible("ANNOUNCEMENT", "校园", true, 0, 20, principal)
				.getData();

		assertThat(page.content()).containsExactly(publication);
		assertThat(page.totalElements()).isEqualTo(1);
		assertThat(page.totalPages()).isEqualTo(1);
		assertThat(page.number()).isZero();
		assertThat(page.first()).isTrue();
		assertThat(page.last()).isTrue();
	}
}
