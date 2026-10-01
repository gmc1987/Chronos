package com.chronos.education.meeting.controller;

import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.chronos.commons.model.ResultData;

import jakarta.persistence.OptimisticLockException;

@RestControllerAdvice(basePackageClasses = MeetingCenterController.class)
public class MeetingApiExceptionHandler {
	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ResultData<?>> badRequest(IllegalArgumentException exception) {
		return response(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ResultData<?>> forbidden(AccessDeniedException exception) {
		return response(HttpStatus.FORBIDDEN, exception.getMessage());
	}

	@ExceptionHandler({IllegalStateException.class, OptimisticLockException.class,
			DataIntegrityViolationException.class})
	ResponseEntity<ResultData<?>> conflict(RuntimeException exception) {
		return response(HttpStatus.CONFLICT, exception.getMessage());
	}

	private ResponseEntity<ResultData<?>> response(HttpStatus status, String message) {
		String value = message == null ? status.getReasonPhrase() : message;
		return ResponseEntity.status(status).body(ResultData.builder()
				.code(String.valueOf(status.value()))
				.msg(value)
				.data(Map.of("error", value))
				.build());
	}
}
