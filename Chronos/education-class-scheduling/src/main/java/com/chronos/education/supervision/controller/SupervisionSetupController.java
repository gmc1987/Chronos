package com.chronos.education.supervision.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.service.SchoolAssessmentIdentityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/education/supervision/setup")
@PreAuthorize("hasAuthority('education:supervision:assignment:create')")
public class SupervisionSetupController {
	private final SchoolAssessmentIdentityService identities;

	@PostMapping("/teachers")
	public ResultData<Teacher> register(@RequestParam String schoolId, @Valid @RequestBody TeacherInput body,
			Authentication actor) {
		var profile = identities.registerTeacher(schoolId, body.code(), body.name(), actor.getName());
		return ok(new Teacher(profile.getId(), profile.getTeacherNo(), profile.getTeacherName()));
	}

	@PostMapping("/teacher-links")
	public ResultData<Void> bind(@RequestParam String schoolId, @Valid @RequestBody AccountLink body,
			Authentication actor) {
		identities.linkAccount(schoolId, new SchoolAssessmentIdentityService.AccountLink(
				"TEACHER", body.teacherNo(), body.username()), actor.getName());
		return ok(null);
	}

	public record TeacherInput(@NotBlank @Size(max = 32) String code, @NotBlank @Size(max = 100) String name) {}
	public record AccountLink(@NotBlank @Size(max = 64) String teacherNo, @NotBlank @Size(max = 100) String username) {}
	public record Teacher(String id, String code, String name) {}

	private static <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
