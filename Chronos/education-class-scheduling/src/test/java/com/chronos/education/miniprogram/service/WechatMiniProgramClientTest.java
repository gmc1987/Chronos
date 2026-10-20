package com.chronos.education.miniprogram.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import com.fasterxml.jackson.databind.ObjectMapper;

class WechatMiniProgramClientTest {
	@Test
	void localMockExchangesDeterministicWechatCredentials() {
		WechatMiniProgramClient client = new WechatMiniProgramClient(
				new ObjectMapper(), new StandardEnvironment(), "", "", true);

		assertThat(client.exchangeLoginCode("mock:openid-001").openId()).isEqualTo("openid-001");
		assertThat(client.exchangePhoneCode("mock:13800138000")).isEqualTo("13800138000");
		assertThat(client.identitySource()).isEqualTo("WXMP:LOCAL_MOCK");
	}

	@Test
	void productionRejectsMockIdentityMode() {
		StandardEnvironment environment = new StandardEnvironment();
		environment.setActiveProfiles("prod");

		assertThatThrownBy(() -> new WechatMiniProgramClient(
				new ObjectMapper(), environment, "", "", true))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("生产环境禁止启用微信小程序模拟身份");
	}
}
