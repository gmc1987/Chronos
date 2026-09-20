package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.StudentStatusChange;
import com.chronos.education.scheduling.model.dto.StudentStatusChangeCommand;
import com.chronos.education.scheduling.model.dto.StudentStatusDecisionCommand;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.StudentStatusChangeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 学籍异动独立命令入口，普通学生档案更新不能替代审批生效。 */
@RestController
@RequestMapping("/admin/education/student-status-changes")
public class StudentStatusChangeController {
	private final StudentStatusChangeService changes;
	private final EducationDataScopeService dataScopes;

	public StudentStatusChangeController(
			StudentStatusChangeService changes,
			EducationDataScopeService dataScopes) {
		this.changes = changes;
		this.dataScopes = dataScopes;
	}

	@GetMapping
	@PreAuthorize("@iamAuthorization.any(authentication,'education:student:view','education:student:manage')")
	public ResultData<PageView<StudentStatusChange>> page(
			@RequestParam String studentId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			Authentication authentication) {
		dataScopes.assertStudentAccess(dataScopes.resolve(authentication.getName()), studentId);
		return ok(PageView.from(changes.page(studentId, page, size)));
	}

	@PostMapping("/{studentId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:student:update','education:student:manage')")
	public ResultData<StudentStatusChange> request(
			@PathVariable String studentId,
			@RequestBody StudentStatusChangeCommand command,
			Authentication authentication) {
		var scope = dataScopes.resolve(authentication.getName());
		dataScopes.assertStudentAccess(scope, studentId);
		if (command.targetClassId() != null && !command.targetClassId().isBlank()) {
			dataScopes.assertClassAccess(scope, command.targetClassId());
		}
		return ok(changes.request(studentId, command, authentication.getName()));
	}

	@PostMapping("/{id}/approve")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:student:manage')")
	public ResultData<StudentStatusChange> approve(
			@PathVariable String id,
			@RequestBody(required = false) StudentStatusDecisionCommand command,
			Authentication authentication) {
		// 学籍审批会改变跨班级关系，当前阶段只允许全校数据范围管理员执行。
		dataScopes.assertFullAccess(dataScopes.resolve(authentication.getName()));
		return ok(changes.approve(
				id,
				command == null ? null : command.comment(),
				authentication.getName()));
	}

	@PostMapping("/{id}/reject")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:student:manage')")
	public ResultData<StudentStatusChange> reject(
			@PathVariable String id,
			@RequestBody StudentStatusDecisionCommand command,
			Authentication authentication) {
		dataScopes.assertFullAccess(dataScopes.resolve(authentication.getName()));
		return ok(changes.reject(id, command.comment(), authentication.getName()));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder()
				.code("200")
				.msg("success")
				.data(data)
				.build();
	}
}
