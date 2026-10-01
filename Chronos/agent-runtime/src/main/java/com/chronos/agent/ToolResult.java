package com.chronos.agent;

/** Stable result envelope for tool execution and audit summaries. */
public record ToolResult<T>(
		String resultCode,
		T data,
		String domainVersion,
		String summary) {
	public static <T> ToolResult<T> success(
			T data,
			String domainVersion,
			String summary) {
		return new ToolResult<>("OK", data, domainVersion, summary);
	}

	public static <T> ToolResult<T> failure(String resultCode, String summary) {
		if (resultCode == null || resultCode.isBlank() || "OK".equals(resultCode)) {
			throw new IllegalArgumentException("失败结果必须提供非 OK 错误码");
		}
		return new ToolResult<>(resultCode, null, null, summary);
	}

	public boolean succeeded() {
		return "OK".equals(resultCode);
	}
}
