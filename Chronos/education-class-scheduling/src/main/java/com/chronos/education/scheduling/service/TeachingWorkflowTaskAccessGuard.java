package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.TeachingReviewRecordRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.workflow.WorkflowTaskAccessGuard;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Keep candidate-role workflow tasks inside the reviewer's education data scope. */
@Component
public class TeachingWorkflowTaskAccessGuard implements WorkflowTaskAccessGuard {
	private final TeachingReviewRecordRepository records;
	private final CourseOfferingRepository offerings;
	private final EducationDataScopeService scopes;

	public TeachingWorkflowTaskAccessGuard(
			TeachingReviewRecordRepository records,
			CourseOfferingRepository offerings,
			EducationDataScopeService scopes) {
		this.records = records;
		this.offerings = offerings;
		this.scopes = scopes;
	}

	@Override
	public boolean supports(String flowCode) {
		return "EDU_TEACHING_CONTENT_REVIEW".equals(flowCode);
	}

	@Override
	public boolean canAccess(String businessKey, String actor) {
		var record = records.findByBusinessKey(businessKey).orElse(null);
		if (record == null) return false;
		var scope = scopes.resolve(actor);
		if (scope.fullAccess()) return true;
		if (record.getOfferingId() == null || record.getOfferingId().isBlank()) return false;
		return offerings.findById(record.getOfferingId())
				.map(offering -> scopes.canAccessOffering(scope, offering))
				.orElse(false);
	}

	@Override
	public void requireAccess(String businessKey, String actor) {
		var record = records.findByBusinessKey(businessKey)
				.orElseThrow(() -> new AccessDeniedException("教学审核记录不存在或不可访问"));
		var scope = scopes.resolve(actor);
		if (record.getOfferingId() == null || record.getOfferingId().isBlank()) {
			// Resources without a teaching class require a separate scoped
			// resolver before ordinary reviewers can act on them.
			scopes.assertFullAccess(scope);
			return;
		}
		scopes.assertOfferingAccess(scope, record.getOfferingId());
	}
}
