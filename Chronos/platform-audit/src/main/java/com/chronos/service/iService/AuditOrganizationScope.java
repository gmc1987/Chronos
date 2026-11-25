package com.chronos.service.iService;

import java.util.Set;

import org.springframework.security.core.Authentication;

public interface AuditOrganizationScope {
	/** Null represents global access. An empty set represents no organization access. */
	Set<String> visibleOrganizationIds(Authentication authentication);
}
