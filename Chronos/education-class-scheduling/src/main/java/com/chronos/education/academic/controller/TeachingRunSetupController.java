package com.chronos.education.academic.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.academic.service.TermCoursePlanService;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.Subject;
import com.chronos.education.scheduling.service.AcademicDataService;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.SchoolAssessmentIdentityService;
import com.chronos.education.scheduling.service.SchoolRosterSetupService;
import com.chronos.education.scheduling.service.SchoolRosterSetupService.StudentRow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/education/term-course-plans/setup")
@PreAuthorize("hasAuthority('education:term-course-plan:setup')")
@RequiredArgsConstructor
public class TeachingRunSetupController {
	private final EducationDataScopeService scopes;
	private final AcademicDataService academic;
	private final SchoolRosterSetupService roster;
	private final SchoolAssessmentIdentityService identities;
	private final TeacherAcademicProfileRepository teachers;
	private final TermCoursePlanService plans;

	@PostMapping("/term")
	public ResultData<?> term(@RequestParam String schoolId, @RequestBody SchoolRosterSetupService.TermCommand input,
			Authentication actor) {
		requireSchool(schoolId, actor);
		return ok(roster.createTerm(schoolId, input));
	}

	@PostMapping("/current-term")
	public ResultData<Void> currentTerm(@RequestParam String schoolId, @RequestParam String termCode,
			Authentication actor) {
		plans.setCurrentTerm(schoolId, termCode, actor.getName());
		return ok(null);
	}

	@PostMapping("/subject")
	public ResultData<?> subject(@RequestParam String schoolId, @RequestBody @Valid NamedCode input,
			Authentication actor) {
		requireSchool(schoolId, actor);
		var subject = new Subject();
		subject.setOrganizationId(schoolId);
		subject.setSubjectCode(input.code().strip());
		subject.setSubjectName(input.name().strip());
		subject.setSubjectCategory("PROFESSIONAL");
		return ok(academic.saveSubject(null, subject));
	}

	@PostMapping("/teacher")
	public ResultData<Option> teacher(@RequestParam String schoolId, @RequestBody @Valid NamedCode input,
			Authentication actor) {
		requireSchool(schoolId, actor);
		var teacher = identities.registerTeacher(schoolId, input.code(), input.name(), actor.getName());
		return ok(new Option(teacher.getId(), teacher.getTeacherName(), teacher.getTeacherNo()));
	}

	@GetMapping("/teachers")
	public ResultData<List<Option>> teachers(@RequestParam String schoolId, Authentication actor) {
		requireSchool(schoolId, actor);
		return ok(teachers.findByDepartmentIdAndEnabledTrueOrderByTeacherNo(schoolId).stream()
				.filter(item -> "ACTIVE".equals(item.getEmploymentStatus()))
				.map(item -> new Option(item.getId(), item.getTeacherName(), item.getTeacherNo())).toList());
	}

	@PostMapping("/class-roster")
	public ResultData<SchoolRosterSetupService.RosterResult> classRoster(@RequestParam String schoolId,
			@RequestBody @Valid RosterBatch input, Authentication actor) {
		requireSchool(schoolId, actor);
		return ok(roster.importExistingClassRoster(schoolId, input.classId(), input.rows()));
	}

	@PostMapping("/account-links")
	public ResultData<Void> linkAccount(@RequestParam String schoolId, @RequestBody @Valid AccountCommand input,
			Authentication actor) {
		requireSchool(schoolId, actor);
		identities.linkAccount(schoolId, new SchoolAssessmentIdentityService.AccountLink(input.profileType(),
				input.code(), input.username()), actor.getName());
		return ok(null);
	}

	private void requireSchool(String schoolId, Authentication actor) {
		scopes.scheduleReviewCampuses(actor.getName(), schoolId);
	}

	public record NamedCode(@NotBlank @Size(max = 32) String code, @NotBlank @Size(max = 100) String name) {}
	public record Option(String id, String name, String code) {}
	public record AccountCommand(@NotBlank @Pattern(regexp = "TEACHER|STUDENT") String profileType,
			@NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 100) String username) {}
	public record RosterBatch(@NotBlank @Size(max = 64) String classId,
			@NotNull @Size(min = 1, max = 500) List<@NotNull @Valid StudentRow> rows) {}

	private static <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("success").data(data).build();
	}
}
