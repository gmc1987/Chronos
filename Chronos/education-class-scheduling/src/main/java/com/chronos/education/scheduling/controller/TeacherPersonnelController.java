package com.chronos.education.scheduling.controller;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.service.TeacherPersonnelScheduler;
import com.chronos.education.scheduling.service.TeacherPersonnelDirectory;
import com.chronos.workflow.WorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.chronos.education.scheduling.service.TeacherPersonnelWorkflowService;
import com.chronos.security.DirectoryDataScopeGuard;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class TeacherPersonnelController {
	private final TeacherPersonnelWorkflowService personnel;
	private final TeacherPersonnelScheduler scheduler;
	private final TeacherPersonnelDirectory setup;
	private final WorkflowService workflows;
	private static final String MANAGE = "@iamAuthorization.has(authentication,'education:teacher:business:manage')";
	private static final String REVIEW = "@iamAuthorization.has(authentication,'education:teacher:business:review')";
	private static final Set<String> CHANGE_TYPES = Set.of("ONBOARD", "TERMINATE", "TRANSFER", "SUSPEND", "RESUME");
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

	@GetMapping("/admin/education/teacher-personnel/directory/structure")
	@PreAuthorize(MANAGE)
	public ResultData<TeacherPersonnelDirectory.Structure> structure(@RequestParam String organizationId, Authentication actor) {
		return ok(setup.structure(organizationId, actor));
	}

	@GetMapping("/admin/education/teacher-personnel/directory/employees")
	@PreAuthorize(MANAGE)
	public ResultData<PageView<TeacherPersonnelDirectory.EmployeeOption>> employees(
			@RequestParam String organizationId, @RequestParam(defaultValue = "") String keyword,
			@RequestParam(defaultValue = "0") int page, Authentication actor) {
		return ok(PageView.from(setup.employees(organizationId, keyword, page, actor)));
	}

	@PostMapping("/admin/education/teacher-personnel/directory/onboard")
	@PreAuthorize(MANAGE)
	public ResultData<TeacherPersonnelDirectory.EmployeeOption> prepareOnboard(
			@RequestBody @Valid TeacherPersonnelDirectory.Onboard input, Authentication actor) {
		return ok(setup.prepareOnboard(input, actor));
	}

	@PostMapping("/admin/education/teacher-personnel/directory/transfer")
	@PreAuthorize(MANAGE)
	public ResultData<Void> prepareTransfer(@RequestBody @Valid TeacherPersonnelDirectory.Transfer input, Authentication actor) {
		setup.prepareTransfer(input, actor);
		return ok(null);
	}

	@PostMapping("/admin/education/teacher-personnel/directory/{employeeCode}/handover")
	@PreAuthorize(MANAGE)
	public ResultData<Void> handover(@RequestParam String organizationId, @PathVariable String employeeCode,
			@RequestBody @Valid TeacherPersonnelDirectory.Handover input, Authentication actor) {
		setup.handover(organizationId, employeeCode, input, actor);
		return ok(null);
	}

	@PostMapping("/admin/education/teacher-personnel/requests")
	@PreAuthorize(MANAGE)
	public ResultData<Map<String, String>> start(@RequestBody @Valid Start input, Authentication actor) {
		if (!CHANGE_TYPES.contains(input.type())) throw new IllegalArgumentException("未知教师人事业务");
		var form = new LinkedHashMap<String, Object>();
		form.put("employeeCode", input.employeeCode());
		form.put("organizationCode", setup.schoolCode(input.organizationId(), actor));
		form.put("effectiveDate", input.effectiveDate().toString());
		form.put("reason", input.reason());
		if ("TRANSFER".equals(input.type())) {
			if (input.targetOrganizationId() == null || input.targetOrganizationId().isBlank()) {
				throw new IllegalArgumentException("请选择调入学校");
			}
			form.put("targetOrganizationCode", setup.schoolCode(input.targetOrganizationId(), actor));
		}
		var result = workflows.startByCode(TeacherPersonnelWorkflowService.PREFIX + input.type(), null, form, actor.getName());
		return ok(Map.of("instanceId", result.getId()));
	}

	@PostMapping("/portal/education/teacher-personnel/resignations")
	@PreAuthorize("isAuthenticated()")
	public ResultData<Map<String, String>> resign(@RequestBody @Valid Resignation input, Authentication actor) {
		var result = workflows.startByCode(TeacherPersonnelWorkflowService.PREFIX + "TERMINATE", null,
				Map.of("effectiveDate", input.effectiveDate().toString(), "reason", input.reason()), actor.getName());
		return ok(Map.of("instanceId", result.getId()));
	}

	public record Resignation(@NotNull LocalDate effectiveDate, @NotBlank @Size(max = 2000) String reason) {}

	@GetMapping("/admin/education/teacher-personnel/reviews")
	@PreAuthorize(REVIEW)
	public ResultData<List<Map<String, Object>>> reviews(Authentication actor) {
		return ok(personnel.pendingReviews(workflows.pending(actor.getName()), actor));
	}

	@PostMapping("/admin/education/teacher-personnel/reviews/{taskId}")
	@PreAuthorize(REVIEW)
	@Transactional
	public ResultData<Void> review(@PathVariable String taskId, @RequestBody @Valid Review input, Authentication actor) {
		var selected = personnel.pendingReviews(workflows.pending(actor.getName()), actor).stream()
				.filter(task -> taskId.equals(task.get("id"))).findFirst()
				.orElseThrow(() -> new AccessDeniedException("无权处理该教师人事任务"));
		if (!input.approved() && (input.comment() == null || input.comment().isBlank())) {
			throw new IllegalArgumentException("请输入驳回原因");
		}
		if (Boolean.TRUE.equals(selected.get("claimable"))) workflows.claimTask(taskId, actor.getName());
		if (input.approved()) workflows.completeTask(taskId, true, input.comment(), actor.getName());
		else workflows.rejectTask(taskId, null, input.comment(), actor.getName());
		return ok(null);
	}

	public record Start(@NotBlank String type, @NotBlank @Size(max = 64) String employeeCode,
			@NotBlank @Size(max = 64) String organizationId, @Size(max = 64) String targetOrganizationId,
			@NotNull LocalDate effectiveDate, @NotBlank @Size(max = 2000) String reason) {}
	public record Review(@NotNull Boolean approved, @Size(max = 2000) String comment) {}

	private static <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
