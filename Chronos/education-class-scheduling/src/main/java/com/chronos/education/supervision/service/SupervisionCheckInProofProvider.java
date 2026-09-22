package com.chronos.education.supervision.service;

import com.chronos.education.supervision.model.SupervisionAssignment;

/**
 * Trusted adapter for campus location or QR verification.
 *
 * <p>No implementation is registered until a deployment provides a trusted
 * verifier. The service consequently fails closed instead of accepting a
 * client-provided location or QR value as proof.</p>
 */
public interface SupervisionCheckInProofProvider {
	boolean verify(SupervisionAssignment assignment, String proof);
}
