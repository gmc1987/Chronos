package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import jakarta.persistence.OptimisticLockException;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

/** Keeps validation, visibility, state and concurrency failures explicit to API clients. */
@RestControllerAdvice(basePackageClasses = TeachingPlanLessonController.class)
public class TeachingApiExceptionHandler {
	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ResultData<?>> badRequest(IllegalArgumentException e) { return response(HttpStatus.BAD_REQUEST, e.getMessage()); }
	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ResultData<?>> forbidden(AccessDeniedException e) { return response(HttpStatus.FORBIDDEN, e.getMessage()); }
	@ExceptionHandler(java.util.NoSuchElementException.class)
	ResponseEntity<ResultData<?>> notFound(java.util.NoSuchElementException e) { return response(HttpStatus.NOT_FOUND, e.getMessage()); }
	@ExceptionHandler({OptimisticLockException.class, OptimisticLockingFailureException.class})
	ResponseEntity<ResultData<?>> conflict(RuntimeException e) { return response(HttpStatus.CONFLICT, "数据已被其他人更新，请刷新后重试"); }
	@ExceptionHandler(IllegalStateException.class)
	ResponseEntity<ResultData<?>> stateConflict(IllegalStateException e) { return response(HttpStatus.CONFLICT, e.getMessage()); }
	private ResponseEntity<ResultData<?>> response(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(ResultData.builder().code(String.valueOf(status.value()))
				.msg(message == null ? status.getReasonPhrase() : message)
				.data(Map.of("error", message == null ? status.getReasonPhrase() : message)).build());
	}
}
