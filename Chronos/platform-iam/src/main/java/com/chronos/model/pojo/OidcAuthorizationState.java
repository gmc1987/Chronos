package com.chronos.model.pojo;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "iam_oidc_authorization_state")
@Getter
@Setter
public class OidcAuthorizationState {
	@Id
	@Column(name = "state", length = 256)
	private String state;

	@Column(name = "source_code", nullable = false, length = 64)
	private String sourceCode;

	@Column(name = "redirect_uri", nullable = false, length = 1000)
	private String redirectUri;

	@Column(name = "code_challenge", nullable = false, length = 128)
	private String codeChallenge;

	@Column(name = "nonce", nullable = false, length = 256)
	private String nonce;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "consumed_at")
	private LocalDateTime consumedAt;
}
