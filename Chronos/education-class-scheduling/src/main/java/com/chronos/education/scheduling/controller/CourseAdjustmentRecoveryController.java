package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.CourseAdjustmentRecord;
import com.chronos.education.scheduling.service.CourseAdjustmentApplicationService;
import java.security.Principal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 教务管理员查看并重放调课回写事故。 */
@RestController
@RequestMapping("/admin/education/course-adjustment-incidents")
public class CourseAdjustmentRecoveryController {
	private final CourseAdjustmentApplicationService applicationService;

	public CourseAdjustmentRecoveryController(CourseAdjustmentApplicationService applicationService) {
		this.applicationService = applicationService;
	}

	@GetMapping
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<List<CourseAdjustmentRecord>> failures() {
		return ok(applicationService.failures());
	}

	@PostMapping("/{id}/retry")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:scheduling:manage')")
	public ResultData<CourseAdjustmentRecord> retry(@PathVariable String id, Principal principal) {
		try {
			return ok(applicationService.retry(id, principal.getName()));
		} catch (RuntimeException exception) {
			// retry() 的事务先完整回滚，再用新事务更新失败次数和错误原因。
			return ok(applicationService.recordRetryFailure(id, principal.getName(), exception));
		}
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
