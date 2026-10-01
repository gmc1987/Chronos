package com.chronos.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class TotpVerifierTest {
	@Test
	void acceptsRfc6238CodeAndReportsTheConsumedTimeStep() {
		var matched = TotpVerifier.matchingCounter("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "287082",
				Instant.ofEpochSecond(59));

		assertThat(matched).isPresent();
		assertThat(matched.getAsLong()).isEqualTo(1L);
		assertThat(TotpVerifier.verify("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "287082",
				Instant.ofEpochSecond(59))).isTrue();
	}

	@Test
	void rejectsMalformedCodesWithoutDecodingSecrets() {
		assertThat(TotpVerifier.matchingCounter("not-base32", "12345", Instant.EPOCH)).isEmpty();
	}
}
