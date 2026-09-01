/**
 * 
 */
package com.chronos.commons.eumns;

/**
 * 
 */
public enum HttpRequestCodeEnum {
	
	SUCCEED("200", "成功"),
	FAILED("500", "失败"),
	NOT_FOUND("404", "未找到"),
	BAD_REQUEST("400", "错误的请求"),
	UNAUTHORIZED("401", "未授权"),
	FORBIDDEN("403", "禁止访问");
	
	HttpRequestCodeEnum(String code, String message) {
		this.code = code;
		this.message = message;
	}
	
	private String code;
	private String message;
	
	public String getCode() {
		return code;
	}
	public String getMessage() {
		return message;
	}

}
