package com.chronos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.chronos.Idao.IIdentitySourceRepository;
import com.chronos.model.pojo.IdentitySource;

class OidcAuthorizationServiceTest {
	@Test
	void callbackConsumesStateAndRejectsReplay() throws Exception {
		IIdentitySourceRepository repository = mock(IIdentitySourceRepository.class);
		IdentitySource source = new IdentitySource();
		source.setSourceCode("corp");
		source.setSourceType("OIDC");
		source.setStatus("ACTIVE");
		source.setIssuerUrl("https://id.example.test");
		source.setClientId("chronos");
		source.setConfigJson("{\"redirectUris\":[\"https://app.example.test/callback\"]}");
		when(repository.findBySourceCode("corp")).thenReturn(Optional.of(source));
		OidcAuthorizationService service = new OidcAuthorizationService(repository);
		String verifier = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~";
		String challenge = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
				java.security.MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));

		var start = service.begin("corp", "https://app.example.test/callback", challenge);
		var claims = new OidcAuthorizationService.OidcTokenClaims("https://id.example.test", "subject-1",
				start.nonce());
		assertThat(service.complete("corp", "https://app.example.test/callback", start.state(), "code", verifier,
				claims).subject()).isEqualTo("subject-1");
		assertThatThrownBy(() -> service.complete("corp", "https://app.example.test/callback", start.state(),
				"code", verifier, claims)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("already used");
	}

	@Test
	void rejectsRedirectUrisThatAreNotRegisteredByTheIdentitySource() {
		IIdentitySourceRepository repository = mock(IIdentitySourceRepository.class);
		IdentitySource source = new IdentitySource();
		source.setSourceCode("corp");
		source.setSourceType("OIDC");
		source.setStatus("ACTIVE");
		source.setIssuerUrl("https://id.example.test");
		source.setClientId("chronos");
		source.setConfigJson("{\"redirectUris\":[\"https://app.example.test/callback\"]}");
		when(repository.findBySourceCode("corp")).thenReturn(Optional.of(source));

		OidcAuthorizationService service = new OidcAuthorizationService(repository);

		assertThatThrownBy(() -> service.begin("corp", "https://attacker.example/callback",
				"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("not registered");
	}
}
