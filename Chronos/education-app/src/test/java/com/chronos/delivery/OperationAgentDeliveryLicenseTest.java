package com.chronos.delivery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class OperationAgentDeliveryLicenseTest {
	private final CustomerLicense license = mock(CustomerLicense.class);
	private final DeliveryBoundary boundary = new DeliveryBoundary(license, null, null, null);

	@Test
	void agentAloneAllowsAcademicSetupButRequiresBusinessProductsForOtherTools() {
		when(license.customer()).thenReturn(true);
		var toolLicense = boundary.operationAgentToolLicense();

		assertFalse(toolLicense.allows("academic.term.create"));
		when(license.moduleAllowed("EDU_OPERATIONS_AGENT")).thenReturn(true);
		assertTrue(toolLicense.allows("academic.term.create"));
		assertTrue(toolLicense.allows("academic.bell.configure"));
		assertFalse(toolLicense.allows("semester.context.check"));
		assertFalse(toolLicense.allows("scheduling.publish"));
	}

	@Test
	void unknownAndCrossProductToolsRemainRestrictedInCustomerMode() {
		when(license.customer()).thenReturn(true);
		when(license.moduleAllowed("EDU_OPERATIONS_AGENT")).thenReturn(true);
		when(license.moduleAllowed("EDU_STUDENT_LIFECYCLE")).thenReturn(true);
		var toolLicense = boundary.operationAgentToolLicense();

		assertTrue(toolLicense.allows("admission.enroll.batch"));
		assertFalse(toolLicense.allows("admission.future.tool"));
		assertFalse(toolLicense.allows("admission.accommodation.assign.batch"));
		when(license.moduleAllowed("EDU_ACCOMMODATION")).thenReturn(true);
		assertTrue(toolLicense.allows("admission.accommodation.assign.batch"));
	}
}
