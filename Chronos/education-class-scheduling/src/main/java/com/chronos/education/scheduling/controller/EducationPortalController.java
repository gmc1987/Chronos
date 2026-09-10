package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.service.EducationPortalContributionProvider;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 教师、学生和家长共用的教务门户只读接口。 */
@RestController
public class EducationPortalController {
	private final EducationPortalContributionProvider portal;

	public EducationPortalController(EducationPortalContributionProvider portal) {
		this.portal = portal;
	}

	@GetMapping("/portal/education/student-contexts")
	@PreAuthorize("isAuthenticated()")
	public ResultData<List<Map<String, Object>>> studentContexts(Principal principal) {
		return ok(portal.studentContexts(principal.getName()));
	}

	@GetMapping("/portal/education/schedule")
	@PreAuthorize("isAuthenticated()")
	public ResultData<Map<String, Object>> schedule(
			@RequestParam(required = false) String studentId,
			Principal principal) {
		return ok(portal.personalSchedule(principal.getName(), studentId));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder()
				.code("200")
				.msg("ok")
				.data(data)
				.build();
	}
}
