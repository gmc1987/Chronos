package com.chronos.education.academic.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.academic.service.StudentCourseEvidenceService;
import com.chronos.education.academic.service.StudentCourseEvidenceService.ImportRow;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.CourseCatalogRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.CourseCatalog;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.EducationGrade;
import com.chronos.education.scheduling.model.Major;
import com.chronos.education.scheduling.service.AcademicDataService;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.EducationResourceTransactionLock;
import com.chronos.education.scheduling.service.SchoolRosterSetupService;
import com.chronos.service.iService.IAuditLogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/education/academic-lifecycle/student-setup")
@RequiredArgsConstructor
public class StudentLifecycleSetupController {
	private final EducationDataScopeService scopes;
	private final AcademicTermRepository terms;
	private final CourseCatalogRepository courses;
	private final CourseOfferingRepository offerings;
	private final SchoolRosterSetupService roster;
	private final StudentCourseEvidenceService evidence;
	private final EducationResourceTransactionLock locks;
	private final IAuditLogService audit;
	private final AcademicDataService academic;

	@PostMapping("/term")
	@PreAuthorize("hasAuthority('education:term-registration:manage')")
	public ResultData<?> term(@RequestParam String schoolId, @RequestBody SchoolRosterSetupService.TermCommand input,
			Authentication actor) {
		scopes.assertSchoolAccess(scopes.resolve(actor.getName()), schoolId);
		return ok(roster.createTerm(schoolId, input));
	}

	@PostMapping("/administrative-class")
	@PreAuthorize("hasAuthority('education:class:create')")
	@Transactional
	public ResultData<AdministrativeClass> createClass(@RequestParam String schoolId,
			@RequestBody @Valid ClassCommand input, Authentication actor) {
		scopes.assertSchoolAccess(scopes.resolve(actor.getName()), schoolId);
		var grade = academic.gradeById(input.gradeId());
		var major = academic.majorById(input.majorId());
		if (!schoolId.equals(grade.getOrganizationId()) || !Boolean.TRUE.equals(grade.getEnabled())) {
			throw new IllegalArgumentException("请选择本校启用年级");
		}
		if (!schoolId.equals(major.getOrganizationId()) || !Boolean.TRUE.equals(major.getEnabled())) {
			throw new IllegalArgumentException("请选择本校启用专业");
		}
		var target = new AdministrativeClass();
		target.setCampusId(schoolId);
		target.setGradeId(grade.getId());
		target.setGradeYear(grade.getEnrollmentYear());
		target.setMajorId(major.getId());
		target.setClassCode(input.code().strip());
		target.setClassName(input.name().strip());
		var saved = academic.saveAdministrativeClass(null, target);
		audit.log(actor.getName(), "EDU_STUDENT_CLASS_CREATE", "classId=" + saved.getId(), schoolId);
		return ok(saved);
	}

	@PostMapping("/grade")
	@PreAuthorize("hasAuthority('education:grade:create')")
	public ResultData<EducationGrade> createGrade(@RequestParam String schoolId,
			@RequestBody @Valid GradeCommand input, Authentication actor) {
		scopes.assertSchoolAccess(scopes.resolve(actor.getName()), schoolId);
		var grade = new EducationGrade();
		grade.setOrganizationId(schoolId);
		grade.setGradeCode(input.code().strip());
		grade.setGradeName(input.name().strip());
		grade.setEnrollmentYear(input.year());
		grade.setEducationStage("HIGHER_EDUCATION");
		return ok(academic.saveGrade(null, grade));
	}

	@PostMapping("/major")
	@PreAuthorize("hasAuthority('education:class:create')")
	public ResultData<Major> createMajor(@RequestParam String schoolId,
			@RequestBody @Valid MajorCommand input, Authentication actor) {
		scopes.assertSchoolAccess(scopes.resolve(actor.getName()), schoolId);
		var major = new Major();
		major.setOrganizationId(schoolId);
		major.setMajorCode(input.code().strip());
		major.setMajorName(input.name().strip());
		major.setSchoolingYears(input.years());
		return ok(academic.saveMajor(null, major));
	}

	@PostMapping("/course")
	@PreAuthorize("hasAuthority('education:course:create')")
	public ResultData<CourseCatalog> createCourse(@RequestParam String schoolId,
			@RequestBody @Valid CourseCommand input, Authentication actor) {
		scopes.assertSchoolAccess(scopes.resolve(actor.getName()), schoolId);
		var major = academic.majorById(input.majorId());
		if (!schoolId.equals(major.getOrganizationId()) || !Boolean.TRUE.equals(major.getEnabled())) {
			throw new IllegalArgumentException("请选择本校启用专业");
		}
		var course = new CourseCatalog();
		course.setOrganizationId(schoolId);
		if (input.subjectId() != null && !input.subjectId().isBlank()) {
			var subject = academic.subjectById(input.subjectId());
			if (!schoolId.equals(subject.getOrganizationId()) || !Boolean.TRUE.equals(subject.getEnabled())) {
				throw new IllegalArgumentException("请选择本校启用学科");
			}
			course.setSubjectId(subject.getId());
		}
		course.setCourseCode(input.code().strip());
		course.setCourseName(input.name().strip());
		course.setMajorIds(Set.of(major.getId()));
		course.setTheoryHours(input.theoryHours());
		course.setPracticeHours(input.practiceHours());
		course.setTotalHours(input.theoryHours() + input.practiceHours());
		course.setCredits(input.credits());
		return ok(academic.saveCourse(null, course));
	}

	@PostMapping("/sections")
	@PreAuthorize("hasAuthority('education:course-enrollment:manage')")
	@Transactional
	public ResultData<Integer> sections(@RequestParam String schoolId, @RequestBody @Valid Sections input,
			Authentication actor) {
		scopes.assertSchoolAccess(scopes.resolve(actor.getName()), schoolId);
		var term = terms.findByTermCode(input.termCode()).filter(item -> schoolId.equals(item.getOrganizationId())
				&& "ACTIVE".equals(item.getStatus())).orElseThrow(() -> new IllegalArgumentException("请选择本校启用学期"));
		locks.lockSemester(term.getTermCode());
		var seen = new HashSet<String>();
		for (var row : input.rows()) {
			var code = row.sectionCode().strip();
			if (!seen.add(code) || offerings.existsBySemesterCodeAndOfferingCode(term.getTermCode(), code)) {
				throw new IllegalArgumentException("该学期已有此教学班编码：" + code);
			}
			var course = courses.findByCourseCode(row.courseCode().strip())
					.filter(item -> schoolId.equals(item.getOrganizationId()) && Boolean.TRUE.equals(item.getEnabled()))
					.orElseThrow(() -> new IllegalArgumentException("请选择本校启用课程：" + row.courseCode()));
			var section = new CourseOffering();
			section.setSemesterCode(term.getTermCode());
			section.setOfferingCode(code);
			section.setCourseCode(course.getCourseCode());
			section.setCourseName(course.getCourseName());
			section.setTeachingClassName(row.sectionName().strip());
			section.setTeacherName(row.teacherName() == null ? "" : row.teacherName().strip());
			section.setCampusId(schoolId);
			section.setStatus(CourseOffering.ENROLLMENT_ONLY);
			offerings.save(section);
		}
		audit.log(actor.getName(), "EDU_STUDENT_SECTIONS_IMPORT", "rows=" + input.rows().size(), schoolId);
		return ok(input.rows().size());
	}

	@PostMapping("/course-evidence")
	@PreAuthorize("hasAuthority('education:graduation-audit:manage')")
	public ResultData<Integer> importEvidence(@RequestParam String schoolId, @RequestBody @Valid EvidenceBatch input,
			Authentication actor) {
		return ok(evidence.importEvidence(schoolId, input.rows(), actor.getName()));
	}

	public record Section(@NotBlank @Size(max = 64) String sectionCode, @NotBlank @Size(max = 128) String sectionName,
			@NotBlank @Size(max = 64) String courseCode, @Size(max = 128) String teacherName) {}
	public record Sections(@NotBlank @Size(max = 128) String termCode,
			@NotNull @Size(min = 1, max = 500) List<@NotNull @Valid Section> rows) {}
	public record EvidenceBatch(@NotNull @Size(min = 1, max = 500)
			List<@NotNull @Valid ImportRow> rows) {}
	public record ClassCommand(@NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 128) String name,
			@NotBlank @Size(max = 64) String gradeId, @NotBlank @Size(max = 64) String majorId) {}
	public record GradeCommand(@NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 128) String name,
			@NotNull @Min(2000) @Max(2100) Integer year) {}
	public record MajorCommand(@NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 128) String name,
			@NotNull @Min(1) @Max(10) Integer years) {}
	public record CourseCommand(@NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 128) String name,
			@NotBlank @Size(max = 64) String majorId, @NotNull @Min(0) @Max(10000) Integer theoryHours,
			@NotNull @Min(0) @Max(10000) Integer practiceHours,
			@NotNull @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.Digits(integer = 4, fraction = 2)
			BigDecimal credits, @Size(max = 64) String subjectId) {}

	private static <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("success").data(data).build();
	}
}
