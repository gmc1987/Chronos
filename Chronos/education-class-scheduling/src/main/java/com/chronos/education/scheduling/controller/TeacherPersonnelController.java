package com.chronos.education.scheduling.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.service.TeacherPersonnelScheduler;
import com.chronos.education.scheduling.service.TeacherPersonnelWorkflowService;
import com.chronos.security.DirectoryDataScopeGuard;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class TeacherPersonnelController {
	private final TeacherPersonnelWorkflowService personnel;
	private final TeacherPersonnelScheduler scheduler;
	private final DirectoryDataScopeGuard directory;

	@GetMapping("/admin/education/teacher-personnel")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teacher:business:view','education:teacher:business:manage')")
	public ResultData<List<Map<String, Object>>> list(@RequestParam String organizationId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "") String status, Authentication actor) {
		directory.requireOrganization(actor, organizationId);
		return ok(personnel.list(organizationId, "", status, Math.max(0, page), 20));
	}

	@GetMapping("/portal/education/teacher-personnel/mine")
	@PreAuthorize("isAuthenticated()")
	public ResultData<List<Map<String, Object>>> mine(@RequestParam(defaultValue = "0") int page, Authentication actor) {
		return ok(personnel.list("", actor.getName(), "", Math.max(0, page), 20));
	}

	@PostMapping("/admin/education/teacher-personnel/{id}/retry")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teacher:business:update','education:teacher:business:manage')")
	public ResultData<Void> retry(@PathVariable String id, Authentication actor) {
		var item = personnel.detail(id);
		directory.requireOrganization(actor, item.organizationId());
		if (item.targetOrganizationId() != null) directory.requireOrganization(actor, item.targetOrganizationId());
		if (!"APPLY_FAILED".equals(item.status())) throw new IllegalStateException("只有生效失败的申请可以重试");
		scheduler.apply(id);
		return ok(null);
	}

	private static <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
