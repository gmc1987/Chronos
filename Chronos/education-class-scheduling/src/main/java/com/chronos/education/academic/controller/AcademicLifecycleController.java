package com.chronos.education.academic.controller;

import static com.chronos.education.academic.dto.AcademicLifecycleDtos.*;

import com.chronos.commons.model.ResultData;
import com.chronos.education.academic.model.CourseEnrollment;
import com.chronos.education.academic.model.GraduationAudit;
import com.chronos.education.academic.model.TermRegistration;
import com.chronos.education.academic.service.AcademicLifecycleService;
import com.chronos.education.scheduling.service.AcademicCatalogImportService;
import com.chronos.education.scheduling.service.AcademicDataService;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.ClassSchedulingService;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.StudentProfile;
import java.util.Set;
import java.util.Locale;
import com.chronos.education.scheduling.service.AcademicCatalogImportService.Kind;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 培养方案、注册、选退课和毕业审核的人工业务入口。 */
@RestController
@RequestMapping("/admin/education/academic-lifecycle")
public class AcademicLifecycleController {
	private final AcademicLifecycleService service;
	private final AcademicCatalogImportService imports;
	private final AcademicDataService academic;
	private final EducationDataScopeService scopes;
	private final ClassSchedulingService scheduling;
	private final AcademicTermRepository terms;

	public AcademicLifecycleController(AcademicLifecycleService service, AcademicCatalogImportService imports,
			AcademicDataService academic, EducationDataScopeService scopes, ClassSchedulingService scheduling,
			AcademicTermRepository terms) {
		this.service = service;
		this.imports = imports;
		this.academic = academic;
		this.scopes = scopes;
		this.scheduling = scheduling;
		this.terms = terms;
	}

	@GetMapping("/programs")
	@PreAuthorize("hasAuthority('education:academic-program:view')")
	public ResultData<List<ProgramView>> programs(@RequestParam String schoolId, Authentication authentication) {
		return ok(service.programs(schoolId, authentication.getName()));
	}

	@GetMapping("/term-options")
	@PreAuthorize("hasAnyAuthority('education:term-registration:view','education:course-enrollment:view')")
	public ResultData<?> termOptions(@RequestParam String schoolId, Authentication actor) {
		scopes.assertSchoolAccess(scopes.resolve(actor.getName()), schoolId);
		return ok(terms.findByOrganizationIdOrderByStartDateDesc(schoolId));
	}

	@GetMapping("/student-options")
	@PreAuthorize("hasAnyAuthority('education:term-registration:view','education:course-enrollment:view','education:graduation-audit:view')")
	public ResultData<List<StudentOption>> studentOptions(@RequestParam String schoolId,
			@RequestParam(required = false) Set<String> ids, @RequestParam(required = false) String keyword,
			Authentication actor) {
		var scope = scopes.resolve(actor.getName());
		scopes.assertSchoolAccess(scope, schoolId);
		var campuses = scopes.scheduleOrganizationCampuses(scope, schoolId);
		if (ids == null || ids.isEmpty()) {
			return ok(academic.scheduleStudentOptions(scope, keyword, 0, 10, campuses).stream()
					.map(StudentOption::from).toList());
		}
		if (ids.size() > 100) throw new IllegalArgumentException("每次最多查询 100 名学生");
		var classIds = academic.administrativeClasses(scope).stream()
				.filter(item -> campuses.contains(item.getCampusId())).map(item -> item.getId())
				.collect(java.util.stream.Collectors.toSet());
		return ok(academic.studentsByIds(List.copyOf(ids)).stream()
				.filter(student -> classIds.contains(student.getAdministrativeClassId()) && scopes.canAccessStudent(scope, student))
				.map(StudentOption::from).toList());
	}

	@GetMapping("/offering-options")
	@PreAuthorize("hasAuthority('education:course-enrollment:view')")
	public ResultData<List<OfferingOption>> offeringOptions(@RequestParam String schoolId, @RequestParam String termCode,
			@RequestParam(required = false) Set<String> ids, @RequestParam(required = false) String keyword,
			Authentication actor) {
		var scope = scopes.resolve(actor.getName());
		scopes.assertSchoolAccess(scope, schoolId);
		var campuses = scopes.scheduleOrganizationCampuses(scope, schoolId);
		terms.findByTermCode(termCode).filter(term -> schoolId.equals(term.getOrganizationId()))
				.orElseThrow(() -> new IllegalArgumentException("所选学校下没有该学期"));
		var visible = scopes.visibleOfferings(scope, scheduling.offerings(termCode)).stream()
				.filter(offering -> campuses.contains(offering.getCampusId()));
		if (ids != null && !ids.isEmpty()) {
			if (ids.size() > 100) throw new IllegalArgumentException("每次最多查询 100 条教学任务");
			return ok(visible.filter(offering -> ids.contains(offering.getId())).map(OfferingOption::from).toList());
		}
		String text = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
		return ok(visible.filter(offering -> "ACTIVE".equals(offering.getStatus()))
				.filter(offering -> (offering.getCourseCode() + " " + offering.getCourseName() + " " + offering.getTeachingClassName())
						.toLowerCase(Locale.ROOT).contains(text))
				.limit(20).map(OfferingOption::from).toList());
	}

	public record StudentOption(String id, String studentNo, String studentName, String majorId, Integer gradeYear) {
		private static StudentOption from(StudentProfile student) {
			return new StudentOption(student.getId(), student.getStudentNo(), student.getStudentName(), student.getMajorId(),
					student.getGradeYear());
		}
	}

	public record OfferingOption(String id, String courseName, String teachingClassName) {
		private static OfferingOption from(CourseOffering offering) {
			return new OfferingOption(offering.getId(), offering.getCourseName(), offering.getTeachingClassName());
		}
	}

	@GetMapping("/program-options")
	@PreAuthorize("hasAnyAuthority('education:term-registration:view','education:graduation-audit:view')")
	public ResultData<List<ProgramView>> programOptions(@RequestParam String schoolId, Authentication authentication) {
		return ok(service.programs(schoolId, authentication.getName()).stream()
				.filter(program -> !"DRAFT".equals(program.status()) && !"SUBMITTED".equals(program.status())).toList());
	}

	@GetMapping("/program-import/{kind}/template")
	@PreAuthorize("hasAuthority('education:academic-program:manage')")
	public ResponseEntity<byte[]> programTemplate(@PathVariable Kind kind) throws IOException {
		checkProgramImport(kind);
		return AcademicCatalogImportService.xlsx(imports.template(kind), "academic-program.xlsx");
	}

	@PostMapping("/program-import/{kind}")
	@PreAuthorize("hasAuthority('education:academic-program:manage')")
	public ResultData<?> importPrograms(@PathVariable Kind kind, @RequestParam String organizationId,
			@RequestParam MultipartFile file, @RequestParam(defaultValue = "true") boolean dryRun,
			Authentication authentication) throws IOException {
		checkProgramImport(kind);
		var rows = imports.readRows(kind, file);
		var errors = service.importProgramRows(organizationId, rows, kind == Kind.PROGRAM_COURSES, dryRun,
				authentication.getName());
		return ok(new AcademicCatalogImportService.Result(rows.size(), rows.size() - errors.size(), errors));
	}

	private void checkProgramImport(Kind kind) {
		if (kind != Kind.PROGRAMS && kind != Kind.PROGRAM_COURSES) {
			throw new IllegalArgumentException("请选择培养方案导入模板");
		}
	}

	@PostMapping("/programs")
	@PreAuthorize("hasAuthority('education:academic-program:manage')")
	public ResultData<ProgramView> createProgram(
			@RequestBody ProgramCommand command,
			Authentication authentication) {
		return ok(service.createProgram(command, authentication.getName()));
	}

	@PutMapping("/programs/{id}")
	@PreAuthorize("hasAuthority('education:academic-program:manage')")
	public ResultData<ProgramView> updateProgram(@PathVariable String id, @RequestParam Long rowVersion,
			@RequestBody ProgramCommand command, Authentication authentication) {
		return ok(service.updateProgram(id, rowVersion, command, authentication.getName()));
	}

	@DeleteMapping("/programs/{id}")
	@PreAuthorize("hasAuthority('education:academic-program:manage')")
	public ResultData<Void> deleteProgram(@PathVariable String id, @RequestParam Long rowVersion,
			Authentication authentication) {
		service.deleteProgram(id, rowVersion, authentication.getName());
		return ok(null);
	}

	@GetMapping("/programs/{id}/course-options")
	@PreAuthorize("hasAuthority('education:academic-program:manage')")
	public ResultData<?> programCourseOptions(@PathVariable String id, Authentication authentication) {
		return ok(service.programCourseOptions(id, authentication.getName()));
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

	@PostMapping("/programs/batch-submit")
	@PreAuthorize("hasAuthority('education:academic-program:manage')")
	public ResultData<Integer> submitPrograms(
			@RequestBody ProgramBatchSubmitCommand command, Authentication authentication) {
		return ok(service.submitPrograms(command, authentication.getName()));
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
