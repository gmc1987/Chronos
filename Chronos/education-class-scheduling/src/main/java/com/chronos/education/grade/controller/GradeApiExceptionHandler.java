package com.chronos.education.grade.controller;

import com.chronos.commons.model.ResultData;
import jakarta.persistence.OptimisticLockException;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = GradeCenterController.class)
public class GradeApiExceptionHandler {
	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ResultData<?>> forbidden(AccessDeniedException exception) {
		return response(HttpStatus.FORBIDDEN, exception.getMessage());
	}

	@ExceptionHandler({OptimisticLockException.class, OptimisticLockingFailureException.class})
	ResponseEntity<ResultData<?>> conflict(RuntimeException exception) {
		return response(HttpStatus.CONFLICT, "数据已被其他人更新，请刷新后重试");
	}

	@ExceptionHandler(IllegalStateException.class)
	ResponseEntity<ResultData<?>> stateConflict(IllegalStateException exception) {
		return response(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ResultData<?>> badRequest(IllegalArgumentException exception) {
		return response(HttpStatus.BAD_REQUEST, exception.getMessage());
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
