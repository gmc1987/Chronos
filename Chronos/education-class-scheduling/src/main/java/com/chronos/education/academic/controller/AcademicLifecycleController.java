package com.chronos.education.academic.controller;

import static com.chronos.education.academic.dto.AcademicLifecycleDtos.*;

import com.chronos.commons.model.ResultData;
import com.chronos.education.academic.model.CourseEnrollment;
import com.chronos.education.academic.model.GraduationAudit;
import com.chronos.education.academic.model.TermRegistration;
import com.chronos.education.academic.service.AcademicLifecycleService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 培养方案、注册、选退课和毕业审核的人工业务入口。 */
@RestController
@RequestMapping("/admin/education/academic-lifecycle")
public class AcademicLifecycleController {
	private final AcademicLifecycleService service;

	public AcademicLifecycleController(AcademicLifecycleService service) {
		this.service = service;
	}

	@GetMapping("/programs")
	@PreAuthorize("hasAuthority('education:academic-program:view')")
	public ResultData<List<ProgramView>> programs(@RequestParam String schoolId, Authentication authentication) {
		return ok(service.programs(schoolId, authentication.getName()));
	}

	@PostMapping("/programs")
	@PreAuthorize("hasAuthority('education:academic-program:manage')")
	public ResultData<ProgramView> createProgram(
			@RequestBody ProgramCommand command,
			Authentication authentication) {
		return ok(service.createProgram(command, authentication.getName()));
	}

	@PostMapping("/programs/{id}/courses")
	@PreAuthorize("hasAuthority('education:academic-program:manage')")
	public ResultData<ProgramView> addCourse(
			@PathVariable String id,
			@RequestBody ProgramCourseCommand command,
			Authentication authentication) {
		return ok(service.addCourse(id, command, authentication.getName()));
	}

	@PostMapping("/programs/{id}/submit")
	@PreAuthorize("hasAuthority('education:academic-program:manage')")
	public ResultData<ProgramView> submitProgram(@PathVariable String id, Authentication authentication) {
		return ok(service.submitProgram(id, authentication.getName()));
	}

	@PostMapping("/programs/{id}/publish")
	@PreAuthorize("hasAuthority('education:academic-program:review')")
	public ResultData<ProgramView> publishProgram(@PathVariable String id, Authentication authentication) {
		return ok(service.publishProgram(id, authentication.getName()));
	}

	@GetMapping("/registrations")
	@PreAuthorize("hasAuthority('education:term-registration:view')")
	public ResultData<List<TermRegistration>> registrations(
			@RequestParam String schoolId,
			@RequestParam String termCode,
			Authentication authentication) {
		return ok(service.registrations(schoolId, termCode, authentication.getName()));
	}

	@PostMapping("/registrations")
	@PreAuthorize("hasAuthority('education:term-registration:manage')")
	public ResultData<TermRegistration> submitRegistration(
			@RequestBody RegistrationCommand command,
			Authentication authentication) {
		return ok(service.submitRegistration(command, authentication.getName()));
	}

	@PostMapping("/registrations/{id}/decision")
	@PreAuthorize("hasAuthority('education:term-registration:manage')")
	public ResultData<TermRegistration> decideRegistration(
			@PathVariable String id,
			@RequestBody RegistrationDecisionCommand command,
			Authentication authentication) {
		return ok(service.decideRegistration(id, command, authentication.getName()));
	}

	@GetMapping("/enrollments")
	@PreAuthorize("hasAuthority('education:course-enrollment:view')")
	public ResultData<List<CourseEnrollment>> enrollments(
			@RequestParam String schoolId,
			@RequestParam String termCode,
			Authentication authentication) {
		return ok(service.enrollments(schoolId, termCode, authentication.getName()));
	}

	@PostMapping("/enrollments")
	@PreAuthorize("hasAuthority('education:course-enrollment:manage')")
	public ResultData<CourseEnrollment> enroll(
			@RequestBody EnrollmentCommand command,
			Authentication authentication) {
		return ok(service.enroll(command, authentication.getName()));
	}

	@PostMapping("/enrollments/{id}/withdraw")
	@PreAuthorize("hasAuthority('education:course-enrollment:manage')")
	public ResultData<CourseEnrollment> withdraw(
			@PathVariable String id,
			@RequestBody WithdrawalCommand command,
			Authentication authentication) {
		return ok(service.withdraw(id, command, authentication.getName()));
	}

	@GetMapping("/graduation-audits")
	@PreAuthorize("hasAuthority('education:graduation-audit:view')")
	public ResultData<List<GraduationAudit>> graduationAudits(
			@RequestParam String schoolId,
			Authentication authentication) {
		return ok(service.graduationAudits(schoolId, authentication.getName()));
	}

	@PostMapping("/graduation-audits/evaluate")
	@PreAuthorize("hasAuthority('education:graduation-audit:manage')")
	public ResultData<GraduationAudit> evaluateGraduation(
			@RequestBody GraduationAuditCommand command,
			Authentication authentication) {
		return ok(service.evaluateGraduation(command, authentication.getName()));
	}

	@PostMapping("/graduation-audits/{id}/decision")
	@PreAuthorize("hasAuthority('education:graduation-audit:approve')")
	public ResultData<GraduationAudit> decideGraduation(
			@PathVariable String id,
			@RequestBody GraduationDecisionCommand command,
			Authentication authentication) {
		return ok(service.decideGraduation(id, command, authentication.getName()));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("success").data(data).build();
	}
}
