package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.model.CollaborationFileShare;
import java.util.Arrays;

/** 附件授权与协同中心共用的纯受众判断，避免文件服务和业务服务相互依赖。 */
final class CollaborationShareAudience {
	private CollaborationShareAudience() {
	}

	static boolean canReadInCustomerDeployment(CollaborationFileShare value, String username, String schoolId) {
		if (schoolId == null || !schoolId.equals(value.getOwnerOrganizationId()) || !"ACTIVE".equals(value.getStatus())) {
			return false;
		}
		if ("SCHOOL".equals(value.getAudienceType())) {
			return true;
		}
		return ("PRIVATE".equals(value.getAudienceType()) || "USER".equals(value.getAudienceType()))
				&& canRead(value, username, null);
	}

	static boolean canRead(CollaborationFileShare value, String username, String organizationId) {
		if (username.equals(value.getOwnerUsername())) {
			return true;
		}
		return switch (value.getAudienceType()) {
			case "ALL" -> true;
			case "USER" -> value.getAudienceValue() != null && Arrays.stream(value.getAudienceValue().split(","))
					.map(String::trim).anyMatch(username::equals);
			case "DEPARTMENT" -> organizationId != null && organizationId.equals(value.getAudienceValue());
			default -> false;
		};
	}
}
