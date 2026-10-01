package com.chronos.integration.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CredentialSerializationTest {
	@Test
	void credentialSerializationNeverIncludesCiphertext() throws Exception {
		Credential credential = new Credential();
		credential.setConnectorId("connector");
		credential.setKeyName("Authorization");
		credential.setSecretCiphertext("v1:encrypted-value");

		String json = new ObjectMapper().writeValueAsString(credential);

		assertThat(json).doesNotContain("secretCiphertext").doesNotContain("encrypted-value");
	}
}
