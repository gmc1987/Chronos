package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.CourseAdjustmentRecord;
import com.chronos.education.scheduling.model.CourseAdjustmentIncidentBatchCommand;
import com.chronos.education.scheduling.model.CourseAdjustmentIncidentBatchItem;
import com.chronos.education.scheduling.model.CourseAdjustmentIncidentBatchResult;
import com.chronos.commons.model.PageView;
import com.chronos.education.scheduling.service.CourseAdjustmentApplicationService;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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
	private final EducationDataScopeService dataScopes;

	public CourseAdjustmentRecoveryController(
			CourseAdjustmentApplicationService applicationService,
			EducationDataScopeService dataScopes) {
		this.applicationService = applicationService;
		this.dataScopes = dataScopes;
	}

	@GetMapping
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<PageView<CourseAdjustmentRecord>> failures(
			@RequestParam(defaultValue = "FAILED") String status,
			@RequestParam(required = false) String adjustmentType,
			@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			Authentication authentication) {
		dataScopes.assertFullAccess(dataScopes.resolve(authentication.getName()));
		return ok(PageView.from(applicationService.incidents(
				status,
				adjustmentType,
				keyword,
				page,
				size)));
	}

	@PostMapping("/{id}/retry")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:scheduling:manage')")
	public ResultData<CourseAdjustmentRecord> retry(
			@PathVariable String id,
			Authentication authentication) {
		dataScopes.assertFullAccess(dataScopes.resolve(authentication.getName()));
		try {
			return ok(applicationService.retry(id, authentication.getName()));
		} catch (RuntimeException exception) {
			// retry() 的事务先完整回滚，再用新事务更新失败次数和错误原因。
			return ok(applicationService.recordRetryFailure(
					id,
					authentication.getName(),
					exception));
		}
	}

	@PostMapping("/batch-retry")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:scheduling:manage')")
	public ResultData<CourseAdjustmentIncidentBatchResult> batchRetry(
			@RequestBody CourseAdjustmentIncidentBatchCommand command,
			Authentication authentication) {
		dataScopes.assertFullAccess(dataScopes.resolve(authentication.getName()));
		LinkedHashSet<String> uniqueIds = new LinkedHashSet<>();
		if (command != null && command.ids() != null) {
			command.ids().stream()
					.filter(id -> id != null && !id.isBlank())
					.map(String::trim)
					.forEach(uniqueIds::add);
		}
		if (uniqueIds.isEmpty()) {
			throw new IllegalArgumentException("至少选择一条调课事故");
		}
		if (uniqueIds.size() > 100) {
			throw new IllegalArgumentException("单次最多重放100条调课事故");
		}

		List<CourseAdjustmentIncidentBatchItem> items = new ArrayList<>();
		for (String id : uniqueIds) {
			items.add(retryOne(id, authentication.getName()));
		}
		int succeeded = (int) items.stream()
				.filter(item -> "APPLIED".equals(item.status()))
				.count();
		return ok(new CourseAdjustmentIncidentBatchResult(
				items.size(),
				succeeded,
				items.size() - succeeded,
				List.copyOf(items)));
	}

	private CourseAdjustmentIncidentBatchItem retryOne(String id, String actor) {
		try {
			CourseAdjustmentRecord record = applicationService.retry(id, actor);
			return item(id, record);
		} catch (RuntimeException exception) {
			try {
				return item(id, applicationService.recordRetryFailure(id, actor, exception));
			} catch (RuntimeException recordFailure) {
				return new CourseAdjustmentIncidentBatchItem(
						id,
						"FAILED",
						null,
						recordFailure.getMessage());
			}
		}
	}

	private CourseAdjustmentIncidentBatchItem item(
			String id,
			CourseAdjustmentRecord record) {
		return new CourseAdjustmentIncidentBatchItem(
				id,
				record.getStatus(),
				record.getRetryCount(),
				record.getMessage());
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
