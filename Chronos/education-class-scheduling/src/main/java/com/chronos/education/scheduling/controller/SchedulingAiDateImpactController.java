package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.SchedulingAiDateImpact;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.SchedulingAiDateImpactService;
import com.chronos.education.scheduling.service.SchedulingAiRunService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Agent Run 的日期事实预览，实际补课、考试占课与代课仍由原业务审批接口执行。 */
@RestController
public class SchedulingAiDateImpactController {
	private final SchedulingAiRunService runs;
	private final SchedulingAiDateImpactService impacts;
	private final EducationDataScopeService scopes;

	public SchedulingAiDateImpactController(SchedulingAiRunService runs,
			SchedulingAiDateImpactService impacts,
			EducationDataScopeService scopes) {
		this.runs = runs;
		this.impacts = impacts;
		this.scopes = scopes;
	}

	@GetMapping("/admin/education/scheduling/ai/runs/{id}/date-impact")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:agent:use') && @iamAuthorization.has(authentication,'education:scheduling:manage') && @iamAuthorization.has(authentication,'education:scheduling:ai:use')")
	public ResultData<SchedulingAiDateImpact> impact(@PathVariable String id,
			Authentication authentication) {
		scopes.assertFullAccess(scopes.resolve(authentication.getName()));
		var run = runs.get(id, authentication.getName(), false);
		return ResultData.<SchedulingAiDateImpact>builder()
				.code("200")
				.msg("success")
				.data(impacts.impact(run.plan()))
				.build();
	}
}
