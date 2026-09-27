package com.chronos.service;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.chronos.model.pojo.IdentitySource;

import lombok.RequiredArgsConstructor;

/**
 * Resolves only a secret-manager reference, never a secret stored in the
 * identity source row.
 */
@Component
@RequiredArgsConstructor
public class EnvironmentOidcClientSecretProvider implements OidcClientSecretProvider {
	private final Environment environment;

	@Override
	public String resolve(IdentitySource source) {
		String reference = source.getSecretRef();
		if (reference == null || reference.isBlank()) {
			throw new IllegalStateException("OIDC client secret reference is not configured");
		}
		String secret = environment.getProperty(reference.trim());
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException("OIDC client secret is unavailable");
		}
		return secret;
	}
}
