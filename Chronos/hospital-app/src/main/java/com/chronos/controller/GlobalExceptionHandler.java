package com.chronos.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import com.chronos.commons.model.ResultData;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalExceptionHandler {
	@ExceptionHandler({ MethodArgumentNotValidException.class })
	public ResponseEntity<ResultData<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new HashMap<>();
		ex.getBindingResult().getFieldErrors().forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));
		ResultData<Map<String, String>> rd = ResultData.<Map<String, String>>builder().code("400")
				.msg("validation error").data(errors).build();

		return new ResponseEntity<ResultData<Map<String, String>>>(rd, (HttpStatusCode) HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler({ IllegalArgumentException.class })
	public ResponseEntity<ResultData<String>> handleIllegalArg(IllegalArgumentException ex) {
		ResultData<String> rd = ResultData.<String>builder().code("400").msg(ex.getMessage()).data(null).build();
		return new ResponseEntity<ResultData<String>>(rd, (HttpStatusCode) HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ResultData<String>> handleAccessDenied(AccessDeniedException ex) {
		ResultData<String> rd = ResultData.<String>builder().code("403").msg("无权访问该资源").data(null).build();
		return new ResponseEntity<>(rd, HttpStatus.FORBIDDEN);
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ResultData<String>> handleAuthentication(AuthenticationException ex) {
		ResultData<String> rd = ResultData.<String>builder().code("401").msg("认证已失效，请重新登录").data(null).build();
		return new ResponseEntity<>(rd, HttpStatus.UNAUTHORIZED);
	}

	@ExceptionHandler({ Exception.class })
	public Object handleGeneric(Exception ex, HttpServletRequest request) {
		String uri = request.getRequestURI();
		String accept = request.getHeader("Accept");

		if (isVideoRequest(uri, accept)) {
			return ResponseEntity.status((HttpStatusCode) HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
		ResultData<String> rd = ResultData.<String>builder().code("500").msg("internal error").data(ex.getMessage())
				.build();

		return new ResponseEntity<ResultData<String>>(rd, (HttpStatusCode) HttpStatus.INTERNAL_SERVER_ERROR);
	}

	private boolean isVideoRequest(String uri, String accept) {
		if (uri != null && uri.contains("/videoProxy")) {
			return true;
		}

		if (accept != null && accept.contains("video")) {
			return true;
		}

		return false;
	}
}
