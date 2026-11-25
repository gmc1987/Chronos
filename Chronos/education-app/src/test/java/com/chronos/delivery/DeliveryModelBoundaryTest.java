package com.chronos.delivery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.util.ServletRequestPathUtils;

class DeliveryModelBoundaryTest {
	@Test
	void builtInCollaborationFlowsRequireTheirOwnProduct() {
		var license = mock(CustomerLicense.class);
		when(license.customer()).thenReturn(true);
		var guard = new DeliveryBoundary(license, null, null, null).collaborationWorkflowLicense();
		assertTrue(guard.supports("STAFF_BUSINESS_TRIP_APPROVAL"));
		assertTrue(guard.supports("EDU_STUDENT_LEAVE_APPROVAL"));
		assertThrows(AccessDeniedException.class, () -> guard.validate("operator", Map.of()));
		when(license.moduleAllowed("COLLABORATION_OFFICE")).thenReturn(true);
		guard.validate("operator", Map.of());
	}

	@Test
	void collaborationLicenseDoesNotOpenOtherProductsWorkflowDefinitions() {
		var license = mock(CustomerLicense.class);
		when(license.customer()).thenReturn(true);
		when(license.moduleAllowed("COLLABORATION_OFFICE")).thenReturn(true);
		var policy = new DeliveryBoundary(license, null, null, null).workflowProductAccess();

		assertTrue(policy.allows("STAFF_BUSINESS_TRIP_APPROVAL"));
		assertFalse(policy.allows("EDU_TEACHER_PERSONNEL_ONBOARD"));
		assertFalse(policy.allows("EDU_GRADEBOOK_REVIEW"));
		assertFalse(policy.allows("EDU_COURSE_ADJUSTMENT_APPROVAL"));
		when(license.moduleAllowed("EDU_TEACHING_OPERATIONS")).thenReturn(true);
		assertTrue(policy.allows("EDU_COURSE_ADJUSTMENT_APPROVAL"));
	}

	@Test
	void onlyLicensedDeploymentAdministratorsCanManageModels() throws Exception {
		var license = mock(CustomerLicense.class);
		when(license.customer()).thenReturn(true);
		var registry = new TestRegistry();
		new DeliveryBoundary(license, null, null, null).addInterceptors(registry);
		HandlerInterceptor guard = registry.interceptor();
		var request = new MockHttpServletRequest("GET", "/ai-model/models/list");
		ServletRequestPathUtils.parseAndCache(request);
		var response = new MockHttpServletResponse();
		Object handler = new Object();
		try {
			SecurityContextHolder.getContext().setAuthentication(
					new TestingAuthenticationToken("group", null, "ROLE_CODE_DELIVERY_GROUP_ADMIN"));
			assertThrows(AccessDeniedException.class, () -> guard.preHandle(request, response, handler));
			when(license.modelAdministrationAllowed()).thenReturn(true);
			assertTrue(guard.preHandle(request, response, handler));
			SecurityContextHolder.getContext().setAuthentication(
					new TestingAuthenticationToken("school", null, "ai:model:manage"));
			assertThrows(AccessDeniedException.class, () -> guard.preHandle(request, response, handler));
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	private static final class TestRegistry extends InterceptorRegistry {
		HandlerInterceptor interceptor() {
			return (HandlerInterceptor) getInterceptors().getFirst();
		}
	}
}
