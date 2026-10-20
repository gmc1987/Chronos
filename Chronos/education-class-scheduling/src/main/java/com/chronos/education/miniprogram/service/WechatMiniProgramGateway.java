package com.chronos.education.miniprogram.service;

/** Boundary around WeChat APIs so identity flows can be tested without external credentials. */
public interface WechatMiniProgramGateway {
	record WechatSession(String openId, String unionId) {}

	WechatSession exchangeLoginCode(String code);

	String exchangePhoneCode(String code);

	byte[] createUnlimitedCode(String scene, String page);

	String identitySource();
}
