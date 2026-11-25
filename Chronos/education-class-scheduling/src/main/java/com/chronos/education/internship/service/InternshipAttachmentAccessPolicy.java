package com.chronos.education.internship.service;

import com.chronos.education.internship.dao.InternshipPlacementRepository;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.file.service.ManagedFileAccessPolicy;
import com.chronos.security.IamAuthorization;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class InternshipAttachmentAccessPolicy implements ManagedFileAccessPolicy {
	private final InternshipPlacementRepository placements;
	private final EducationDataScopeService dataScopes;
	private final IamAuthorization authorization;

	public InternshipAttachmentAccessPolicy(InternshipPlacementRepository placements,
			EducationDataScopeService dataScopes, IamAuthorization authorization) {
		this.placements = placements;
		this.dataScopes = dataScopes;
		this.authorization = authorization;
	}

	@Override
	public boolean canRead(String username, String businessType, String businessId) {
		if (!InternshipService.ATTACHMENT_TYPE.equals(businessType) || businessId == null) {
			return false;
		}
		var actor = SecurityContextHolder.getContext().getAuthentication();
		if (actor == null || !username.equals(actor.getName())
				|| !authorization.has(actor, "education:internship:view")) {
			return false;
		}
		var scope = dataScopes.resolve(username);
		return placements.findById(businessId)
				.map(placement -> scope.fullAccess() || scope.campusIds().contains(placement.getSchoolId()))
				.orElse(false);
	}
}
