package com.chronos.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.chronos.commons.model.ResultData;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

class GlobalExceptionHandlerTest {
	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void shouldReturnExplicitBusinessDenialReason() {
		ResponseEntity<ResultData<String>> response = handler.handleAccessDenied(
				new AccessDeniedException("申请人不能审批自己的申请"));

		assertThat(response.getStatusCode().value()).isEqualTo(403);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getCode()).isEqualTo("403");
		assertThat(response.getBody().getMsg()).isEqualTo("申请人不能审批自己的申请");
	}

	@Test
	void shouldUseSafeFallbackWhenDenialHasNoMessage() {
		ResponseEntity<ResultData<String>> response = handler.handleAccessDenied(
				new AccessDeniedException(""));

		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getMsg()).isEqualTo("无权访问该资源");
	}
}
