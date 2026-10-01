package com.chronos.integration.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.integration.service.IntegrationBoundaryException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = IntegrationController.class)
public class IntegrationExceptionHandler {
	@ExceptionHandler(IntegrationBoundaryException.class)
	public ResponseEntity<ResultData<String>> handleBoundary(IntegrationBoundaryException exception) {
		return ResponseEntity.badRequest().body(ResultData.<String>builder()
				.code(exception.getCode())
				.msg(exception.getMessage())
				.data(null)
				.build());
	}
}
