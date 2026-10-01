package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.TeacherEmploymentChange;
import com.chronos.education.scheduling.model.dto.TeacherEmploymentChangeCommand;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.TeacherEmploymentChangeService;
import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TeacherEmploymentChangeController {
	private final TeacherEmploymentChangeService service;
	private final EducationDataScopeService scopes;

	public TeacherEmploymentChangeController(
			TeacherEmploymentChangeService service,
			EducationDataScopeService scopes) {
		this.service = service;
		this.scopes = scopes;
	}

	@GetMapping("/admin/education/teacher-employment-changes")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teacher:business:view','education:teacher:business:manage')")
	public ResultData<PageView<TeacherEmploymentChange>> history(
			@RequestParam String teacherId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			Principal principal) {
		scopes.assertTeacherAccess(scopes.resolve(principal.getName()), teacherId);
		return ok(PageView.from(service.history(teacherId, page, size)));
	}

	@PostMapping("/admin/education/teacher-employment-changes/{teacherId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teacher:business:update','education:teacher:business:manage')")
	public ResultData<TeacherEmploymentChange> register(
			@PathVariable String teacherId,
			@RequestBody TeacherEmploymentChangeCommand command,
			Principal principal) {
		scopes.assertFullAccess(scopes.resolve(principal.getName()));
		return ok(service.register(teacherId, command, principal.getName()));
	}

	@PostMapping("/admin/education/teacher-employment-changes/{id}/cancel")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teacher:business:update','education:teacher:business:manage')")
	public ResultData<TeacherEmploymentChange> cancel(
			@PathVariable String id,
			Principal principal) {
		scopes.assertFullAccess(scopes.resolve(principal.getName()));
		return ok(service.cancel(id, principal.getName()));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
