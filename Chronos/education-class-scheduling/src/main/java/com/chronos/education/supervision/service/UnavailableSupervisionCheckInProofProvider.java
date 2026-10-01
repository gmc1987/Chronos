package com.chronos.education.supervision.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Explicit fail-closed default. A deployment must provide a real SPI bean
 * backed by its trusted location or QR service.
 */
@Component
@ConditionalOnMissingBean(SupervisionCheckInProofProvider.class)
public class UnavailableSupervisionCheckInProofProvider implements SupervisionCheckInProofProvider {
	@Override
	public VerificationResult verify(VerificationRequest request) {
		return new VerificationResult(VerificationCode.CONFIGURATION_MISSING, null, null);
	}
}
