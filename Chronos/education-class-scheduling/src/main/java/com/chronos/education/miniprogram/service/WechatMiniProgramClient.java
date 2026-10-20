package com.chronos.education.miniprogram.service;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Production WeChat client. AppSecret remains server-side and is read from deployment configuration. */
@Service
public class WechatMiniProgramClient implements WechatMiniProgramGateway {
	private static final String API = "https://api.weixin.qq.com";

	private final RestClient http;
	private final ObjectMapper json;
	private final Environment environment;
	private final String appId;
	private final String appSecret;
	private final boolean mockEnabled;
	private volatile AccessToken cachedToken;

	public WechatMiniProgramClient(
			ObjectMapper json,
			Environment environment,
			@Value("${chronos.wechat.mini-program.app-id:}") String appId,
			@Value("${chronos.wechat.mini-program.app-secret:}") String appSecret,
			@Value("${chronos.wechat.mini-program.mock-enabled:false}") boolean mockEnabled) {
		this.http = RestClient.builder().baseUrl(API).build();
		this.json = json;
		this.environment = environment;
		this.appId = appId == null ? "" : appId.trim();
		this.appSecret = appSecret == null ? "" : appSecret.trim();
		this.mockEnabled = mockEnabled;
		if (mockEnabled && isProduction()) {
			throw new IllegalStateException("生产环境禁止启用微信小程序模拟身份");
		}
	}

	@Override
	public WechatSession exchangeLoginCode(String code) {
		if (mockEnabled && code != null && code.startsWith("mock:")) {
			String openId = code.substring("mock:".length()).trim();
			if (openId.isEmpty()) throw new IllegalArgumentException("模拟 OpenID 不能为空");
			return new WechatSession(openId, null);
		}
		requireConfigured();
		if (code == null || code.isBlank()) throw new IllegalArgumentException("微信登录凭证不能为空");
		String uri = UriComponentsBuilder.fromPath("/sns/jscode2session")
				.queryParam("appid", appId)
				.queryParam("secret", appSecret)
				.queryParam("js_code", code.trim())
				.queryParam("grant_type", "authorization_code")
				.build().toUriString();
		Map<String, Object> response = http.get().uri(uri).retrieve().body(Map.class);
		assertWechatSuccess(response, "微信登录失败");
		String openId = text(response, "openid");
		if (openId.isBlank()) throw new IllegalStateException("微信登录未返回 OpenID");
		return new WechatSession(openId, text(response, "unionid"));
	}

	@Override
	public String exchangePhoneCode(String code) {
		if (mockEnabled && code != null && code.startsWith("mock:")) {
			String phone = code.substring("mock:".length()).trim();
			if (!phone.matches("\\d{6,20}")) throw new IllegalArgumentException("模拟手机号格式错误");
			return phone;
		}
		requireConfigured();
		if (code == null || code.isBlank()) throw new IllegalArgumentException("请授权微信手机号完成身份核验");
		Map<String, Object> response = http.post()
				.uri("/wxa/business/getuserphonenumber?access_token={token}", accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("code", code.trim()))
				.retrieve().body(Map.class);
		assertWechatSuccess(response, "微信手机号核验失败");
		Object phoneInfo = response == null ? null : response.get("phone_info");
		if (!(phoneInfo instanceof Map<?, ?> values)) {
			throw new IllegalStateException("微信手机号核验未返回号码");
		}
		Object phoneValue = values.get("purePhoneNumber");
		String phone = phoneValue == null ? "" : String.valueOf(phoneValue);
		if (phone.isBlank()) throw new IllegalStateException("微信手机号核验未返回号码");
		return phone;
	}

	@Override
	public byte[] createUnlimitedCode(String scene, String page) {
		requireConfigured();
		if (scene == null || scene.isBlank() || scene.length() > 32) {
			throw new IllegalArgumentException("小程序码场景参数必须为 1 至 32 个字符");
		}
		byte[] value = http.post()
				.uri("/wxa/getwxacodeunlimit?access_token={token}", accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of(
						"scene", scene,
						"page", page,
						"width", 430,
						"check_path", true,
						"env_version", "release"))
				.retrieve().body(byte[].class);
		if (value == null || value.length == 0) throw new IllegalStateException("微信小程序码生成失败");
		if (value[0] == '{') {
			try {
				Map<String, Object> response = json.readValue(value, new TypeReference<>() {});
				assertWechatSuccess(response, "微信小程序码生成失败");
			} catch (IllegalStateException exception) {
				throw exception;
			} catch (Exception exception) {
				throw new IllegalStateException("微信小程序码响应无法解析", exception);
			}
		}
		return value;
	}

	@Override
	public String identitySource() {
		if (mockEnabled && appId.isBlank()) return "WXMP:LOCAL_MOCK";
		requireConfigured();
		return "WXMP:" + appId;
	}

	private synchronized String accessToken() {
		if (cachedToken != null && cachedToken.expiresAt().isAfter(Instant.now().plusSeconds(120))) {
			return cachedToken.value();
		}
		Map<String, Object> response = http.get()
				.uri("/cgi-bin/token?grant_type=client_credential&appid={appid}&secret={secret}", appId, appSecret)
				.retrieve().body(Map.class);
		assertWechatSuccess(response, "微信接口凭证获取失败");
		String value = text(response, "access_token");
		Number expiresIn = response == null ? null : (Number) response.get("expires_in");
		if (value.isBlank()) throw new IllegalStateException("微信接口凭证为空");
		cachedToken = new AccessToken(value, Instant.now().plusSeconds(expiresIn == null ? 7200 : expiresIn.longValue()));
		return value;
	}

	private void assertWechatSuccess(Map<String, Object> response, String message) {
		if (response == null) throw new IllegalStateException(message + "：响应为空");
		Object errorCode = response.get("errcode");
		if (errorCode != null && !"0".equals(String.valueOf(errorCode))) {
			throw new IllegalStateException(message + "：" + text(response, "errmsg"));
		}
	}

	private String text(Map<String, Object> values, String key) {
		Object value = values == null ? null : values.get(key);
		return value == null ? "" : String.valueOf(value);
	}

	private void requireConfigured() {
		if (mockEnabled) return;
		if (appId.isBlank() || appSecret.isBlank()) {
			throw new IllegalStateException("微信小程序 AppID 或 AppSecret 尚未配置");
		}
	}

	private boolean isProduction() {
		for (String profile : environment.getActiveProfiles()) {
			if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) return true;
		}
		return false;
	}

	private record AccessToken(String value, Instant expiresAt) {}
}
