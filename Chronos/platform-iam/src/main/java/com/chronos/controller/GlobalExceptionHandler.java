package com.chronos.controller;

import com.chronos.commons.model.ResultData;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 所有行业应用统一返回结构化错误，避免异常转发被安全链误映射为 403。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ResultData<Map<String, String>>> handleValidation(
			MethodArgumentNotValidException exception) {
		Map<String, String> errors = new HashMap<>();
		exception.getBindingResult().getFieldErrors()
				.forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
		return ResponseEntity.badRequest().body(ResultData.<Map<String, String>>builder()
				.code("400")
				.msg("validation error")
				.data(errors)
				.build());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ResultData<String>> handleIllegalArgument(
			IllegalArgumentException exception) {
		return ResponseEntity.badRequest().body(error("400", exception.getMessage()));
	}

	/**
	 * 业务状态冲突（例如候选课表基线已过期）需要把可处理原因返回给前端，
	 * 不能降级成没有上下文的 internal error。
	 */
	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ResultData<String>> handleIllegalState(
			IllegalStateException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(error("409", exception.getMessage()));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ResultData<String>> handleAccessDenied(
			AccessDeniedException exception) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(error("403", "无权访问该资源"));
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ResultData<String>> handleAuthentication(
			AuthenticationException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(error("401", "认证已失效，请重新登录"));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<?> handleGeneric(
			Exception exception,
			HttpServletRequest request) {
		LOGGER.error("Unhandled request exception, uri={}", request.getRequestURI(), exception);
		if (isVideoRequest(request)) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(error("500", "internal error"));
	}

	private ResultData<String> error(String code, String message) {
		return ResultData.<String>builder()
				.code(code)
				.msg(message)
				.data(null)
				.build();
	}

	private boolean isVideoRequest(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String accept = request.getHeader("Accept");
		return uri != null && uri.contains("/videoProxy")
				|| accept != null && accept.contains("video");
	}
}
