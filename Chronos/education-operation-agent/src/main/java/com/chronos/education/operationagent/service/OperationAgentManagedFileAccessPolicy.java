package com.chronos.education.operationagent.service;

import java.util.UUID;
import org.springframework.stereotype.Component;
import com.chronos.file.service.ManagedFileAccessPolicy;

/** Restricts operation-agent business files to the user who created the upload. */
@Component
public class OperationAgentManagedFileAccessPolicy implements ManagedFileAccessPolicy {
	public static final String BUSINESS_ATTACHMENT = "EDUCATION_OPERATION_AGENT_FILE";
	/** @deprecated Kept for already uploaded admission rosters. */
	@Deprecated
	public static final String ADMISSION_ROSTER = "EDUCATION_ADMISSION_AGENT";

	public String newBusinessId(String username) {
		return username + ":" + UUID.randomUUID();
	}

	@Override
	public boolean canRead(String username, String businessType, String businessId) {
		return owns(username, businessType, businessId);
	}

	@Override
	public boolean canWrite(String username, String businessType, String businessId) {
		return owns(username, businessType, businessId);
	}

	private boolean owns(String username, String businessType, String businessId) {
		if ((!BUSINESS_ATTACHMENT.equals(businessType) && !ADMISSION_ROSTER.equals(businessType))
				|| username == null || username.isBlank()
				|| businessId == null) {
			return false;
		}
		String prefix = username + ":";
		if (!businessId.startsWith(prefix)) {
			return false;
		}
		try {
			UUID.fromString(businessId.substring(prefix.length()));
			return true;
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}
}
