package com.chronos.commons.eumns;

public enum LogTypeEnum {
	REQUEST("REQUEST", "请求日志"), RESPONSE("RESPONSE", "响应日志"), ERROR("ERROR", "错误日志");

	private final String code;
	private final String description;

	LogTypeEnum(String code, String description) {
		this.code = code;
		this.description = description;
	}

	public String getCode() {
		return this.code;
	}

	public String getDescription() {
		return this.description;
	}
}
