package com.chronos.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import io.jsonwebtoken.Claims;

class JwtUtilTokenUniquenessTest {
	@Test
	void accessAndRefreshTokensHaveUniqueJtiWithinTheSameMillisecond() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty("security.jwt.secret", "test-secret-that-is-long-enough-for-hs256");
		JwtUtil jwt = new JwtUtil(environment);

		String accessOne = jwt.generateAccessToken("alice", Map.of());
		String accessTwo = jwt.generateAccessToken("alice", Map.of());
		String refreshOne = jwt.generateRefreshToken("alice", Map.of());
		String refreshTwo = jwt.generateRefreshToken("alice", Map.of());

		assertThat(jwt.parseToken(accessOne).getId()).isNotEqualTo(jwt.parseToken(accessTwo).getId());
		Claims firstRefresh = jwt.parseToken(refreshOne);
		Claims secondRefresh = jwt.parseToken(refreshTwo);
		assertThat(firstRefresh.getId()).isNotEqualTo(secondRefresh.getId());
		assertThat(firstRefresh.get("tokenType", String.class)).isEqualTo("refresh");
	}
}
