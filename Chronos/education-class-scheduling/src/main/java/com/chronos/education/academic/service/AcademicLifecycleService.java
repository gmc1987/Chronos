package com.chronos.education.academic.service;

import static com.chronos.education.academic.dto.AcademicLifecycleDtos.*;

import com.chronos.education.academic.dao.*;
import com.chronos.education.academic.model.*;
import com.chronos.education.grade.dao.CourseGradeRepository;
import com.chronos.education.grade.dao.GradebookRepository;
import com.chronos.education.grade.model.CourseGrade;
import com.chronos.education.grade.model.Gradebook;
import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.*;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 高职教务生产主链。
 *
 * <p>培养方案是课程要求的权威来源，学期注册是选课前置，正式选退课同时
 * 维护不可变业务台账和教学班执行投影。毕业审核基于已发布成绩生成快照，
 * 后续成绩更正不会静默篡改已经批准的审核结论。</p>
 */
@Service
public class AcademicLifecycleService {
	private static final Set<String> EDUCATION_LEVELS = Set.of("VOCATIONAL", "COLLEGE", "UNDERGRADUATE");
	private static final Set<String> COURSE_NATURES = Set.of("REQUIRED", "ELECTIVE", "LIMITED_ELECTIVE");
	private final AcademicProgramRepository programs;
	private final AcademicProgramCourseRepository programCourses;
	private final TermRegistrationRepository registrations;
	private final CourseEnrollmentRepository enrollments;
	private final GraduationAuditRepository graduationAudits;
	private final MajorRepository majors;
	private final CourseCatalogRepository courses;
	private final AcademicTermRepository terms;
	private final StudentProfileRepository students;
	private final CourseOfferingRepository offerings;
	private final TeachingClassMemberRepository members;
	private final GradebookRepository gradebooks;
	private final CourseGradeRepository courseGrades;
	private final EducationDataScopeService dataScopes;
	private final IAuditLogService audit;
	private final ObjectMapper json;

	public AcademicLifecycleService(
			AcademicProgramRepository programs,
			AcademicProgramCourseRepository programCourses,
			TermRegistrationRepository registrations,
			CourseEnrollmentRepository enrollments,
			GraduationAuditRepository graduationAudits,
			MajorRepository majors,
			CourseCatalogRepository courses,
			AcademicTermRepository terms,
			StudentProfileRepository students,
			CourseOfferingRepository offerings,
			TeachingClassMemberRepository members,
			GradebookRepository gradebooks,
			CourseGradeRepository courseGrades,
			EducationDataScopeService dataScopes,
			IAuditLogService audit,
			ObjectMapper json) {
		this.programs = programs;
		this.programCourses = programCourses;
		this.registrations = registrations;
		this.enrollments = enrollments;
		this.graduationAudits = graduationAudits;
		this.majors = majors;
		this.courses = courses;
		this.terms = terms;
		this.students = students;
		this.offerings = offerings;
		this.members = members;
		this.gradebooks = gradebooks;
		this.courseGrades = courseGrades;
		this.dataScopes = dataScopes;
		this.audit = audit;
		this.json = json;
	}

	@Transactional(readOnly = true)
	public List<ProgramView> programs(String schoolId, String actor) {
		assertSchool(actor, schoolId);
		return programs.findBySchoolIdOrderByCohortYearDescProgramCode(schoolId).stream()
				.map(this::view).toList();
	}

	@Transactional
	public ProgramView createProgram(ProgramCommand command, String actor) {
		validateProgram(command);
		assertSchool(actor, command.schoolId());
		validateProgramMajor(command);
		if (programs.existsBySchoolIdAndProgramCodeAndVersionNo(command.schoolId(), command.programCode().trim(), 1)) {
			throw new IllegalStateException("培养方案编码已存在");
		}
		AcademicProgram program = new AcademicProgram();
		program.setSchoolId(command.schoolId());
		applyProgramFields(program, command);
		program = programs.save(program);
		audit.log(actor, "EDU_ACADEMIC_PROGRAM_CREATE", "programId=" + program.getId());
		return view(program);
	}

	@Transactional
	public ProgramView updateProgram(String id, Long rowVersion, ProgramCommand command, String actor) {
		AcademicProgram program = mutableProgram(id, actor);
		assertProgramVersion(program, rowVersion);
		validateProgram(command);
		if (!program.getSchoolId().equals(command.schoolId())) {
			throw new IllegalArgumentException("培养方案所属学校不能变更");
		}
		validateProgramMajor(command);
		String code = command.programCode().trim();
		if (!program.getProgramCode().equals(code)
				&& programs.existsBySchoolIdAndProgramCodeAndVersionNo(program.getSchoolId(), code, program.getVersionNo())) {
			throw new IllegalStateException("培养方案编码已存在");
		}
		if (!program.getMajorId().equals(command.majorId())) {
			var courseIds = programCourses.findByProgramIdOrderBySuggestedTermNoAscSortOrderAsc(id).stream()
					.map(AcademicProgramCourse::getCourseId).toList();
			if (courses.findAllById(courseIds).stream().anyMatch(course -> !course.appliesToMajor(command.majorId()))) {
				throw new IllegalArgumentException("已有方案课程不适用于所选专业，不能变更专业");
			}
		}
		applyProgramFields(program, command);
		programs.saveAndFlush(program);
		audit.log(actor, "EDU_ACADEMIC_PROGRAM_UPDATE", "programId=" + id);
		return view(program);
	}

	@Transactional
	public void deleteProgram(String id, Long rowVersion, String actor) {
		AcademicProgram program = mutableProgram(id, actor);
		assertProgramVersion(program, rowVersion);
		try {
			programCourses.deleteDraftCourses(id);
			programs.delete(program);
			programs.flush();
		} catch (DataIntegrityViolationException exception) {
			throw new IllegalStateException("培养方案或其课程已被业务记录引用，无法删除", exception);
		}
		audit.log(actor, "EDU_ACADEMIC_PROGRAM_DELETE", "programId=" + id);
	}

	private void assertProgramVersion(AcademicProgram program, Long rowVersion) {
		if (!Objects.equals(program.getRowVersion(), rowVersion)) {
			throw new IllegalStateException("培养方案已变更，请刷新后再操作");
		}
	}

	private void validateProgramMajor(ProgramCommand command) {
		var major = majors.findById(command.majorId()).orElseThrow(() -> new IllegalArgumentException("专业不存在"));
		if (major.getOrganizationId() != null && !command.schoolId().equals(major.getOrganizationId())) {
			throw new IllegalArgumentException("专业不属于所选学校");
		}
	}

	private void applyProgramFields(AcademicProgram program, ProgramCommand command) {
		program.setMajorId(command.majorId());
		program.setCohortYear(command.cohortYear());
		program.setEducationLevel(normalize(command.educationLevel()));
		program.setProgramCode(command.programCode().trim());
		program.setProgramName(command.programName().trim());
		program.setMinimumCredits(command.minimumCredits());
		program.setEffectiveStartDate(command.effectiveStartDate());
		program.setEffectiveEndDate(command.effectiveEndDate());
	}

	@Transactional
	public List<String> importProgramRows(String schoolId, List<List<String>> rows, boolean courseRows, boolean dryRun,
			String actor) {
		assertSchool(actor, schoolId);
		var errors = new ArrayList<String>();
		var writes = new ArrayList<Runnable>();
		var keys = new HashSet<List<String>>();
		var schoolPrograms = programs.findBySchoolIdOrderByCohortYearDescProgramCode(schoolId);
		for (var row : rows) {
			try {
				if (courseRows) {
					var drafts = schoolPrograms.stream()
							.filter(program -> row.get(1).equals(program.getProgramCode()) && "DRAFT".equals(program.getStatus()))
							.toList();
					if (drafts.size() != 1) throw new IllegalArgumentException("未能唯一匹配草稿方案编码");
					var program = drafts.getFirst();
					var course = courses.findByCourseCode(row.get(2))
							.filter(item -> isProgramCourseEligible(item, program))
							.orElseThrow(() -> new IllegalArgumentException("课程不存在、未启用或不适用于方案专业"));
					String nature = normalize(row.get(4));
					int term = Integer.parseInt(row.get(3));
					var credits = row.get(5).isBlank() ? null : new BigDecimal(row.get(5));
					if (term < 1 || !COURSE_NATURES.contains(nature)
							|| credits != null && (credits.signum() < 0 || credits.compareTo(new BigDecimal("999999.99")) > 0 || credits.scale() > 2)) {
						throw new IllegalArgumentException("请检查建议学期、课程性质和计入学分");
					}
					if (programCourses.findByProgramIdAndCourseId(program.getId(), course.getId()).isPresent()
							|| !keys.add(List.of(program.getId(), course.getId()))) {
						throw new IllegalArgumentException("方案课程已存在或文件内重复");
					}
					var command = new ProgramCourseCommand(course.getId(), term, nature, credits, List.of(), null, null);
					writes.add(() -> addCourse(program.getId(), command, actor));
				} else {
					var major = majors.findByMajorCode(row.get(1))
							.filter(item -> Boolean.TRUE.equals(item.getEnabled()))
							.filter(item -> item.getOrganizationId() == null || schoolId.equals(item.getOrganizationId()))
							.orElseThrow(() -> new IllegalArgumentException("专业不存在或不可用"));
					var command = new ProgramCommand(schoolId, major.getId(), Integer.parseInt(row.get(2)), row.get(3),
							row.get(4), row.get(5), new BigDecimal(row.get(6)), null, null);
					validateProgram(command);
					if (command.programCode().length() > 64 || command.programName().length() > 200
							|| command.minimumCredits().compareTo(new BigDecimal("999999.99")) > 0
							|| command.minimumCredits().scale() > 2) {
						throw new IllegalArgumentException("方案编码、名称或最低学分超出允许范围");
					}
					if (schoolPrograms.stream().anyMatch(program -> program.getProgramCode().equals(command.programCode()))
							|| !keys.add(List.of(command.programCode()))) {
						throw new IllegalArgumentException("方案编码已存在或文件内重复");
					}
					writes.add(() -> createProgram(command, actor));
				}
			} catch (NumberFormatException exception) {
				errors.add("第 " + row.getFirst() + " 行：年份、学期或学分格式无效");
			} catch (IllegalArgumentException exception) {
				errors.add("第 " + row.getFirst() + " 行：" + exception.getMessage());
			}
		}
		if (!dryRun && errors.isEmpty()) writes.forEach(Runnable::run);
		return errors;
	}

	@Transactional(readOnly = true)
	public List<CourseCatalog> programCourseOptions(String id, String actor) {
		var program = programs.findById(id).orElseThrow(() -> new IllegalArgumentException("培养方案不存在"));
		assertSchool(actor, program.getSchoolId());
		var added = programCourses.findByProgramIdOrderBySuggestedTermNoAscSortOrderAsc(id).stream()
				.map(AcademicProgramCourse::getCourseId).collect(Collectors.toSet());
		return courses.findByOrganizationIdInOrOrganizationIdIsNullOrderByCourseCode(List.of(program.getSchoolId())).stream()
				.filter(course -> isProgramCourseEligible(course, program) && !added.contains(course.getId()))
				.toList();
	}

	private boolean isProgramCourseEligible(CourseCatalog course, AcademicProgram program) {
		return Boolean.TRUE.equals(course.getEnabled())
				&& (course.getOrganizationId() == null || program.getSchoolId().equals(course.getOrganizationId()))
				&& course.appliesToMajor(program.getMajorId());
	}

	@Transactional
	public ProgramView addCourse(String programId, ProgramCourseCommand command, String actor) {
		AcademicProgram program = mutableProgram(programId, actor);
		CourseCatalog course = courses.findById(command.courseId())
				.orElseThrow(() -> new IllegalArgumentException("课程不存在"));
		if (!isProgramCourseEligible(course, program)) {
			throw new IllegalArgumentException("请选择启用的公共基础课或适用于本专业的课程");
		}
		String nature = normalize(command.courseNature());
		if (!COURSE_NATURES.contains(nature)) {
			throw new IllegalArgumentException("课程性质不支持");
		}
		if (command.suggestedTermNo() == null || command.suggestedTermNo() < 1) {
			throw new IllegalArgumentException("建议开课学期必须大于零");
		}
		if (programCourses.findByProgramIdAndCourseId(programId, course.getId()).isPresent()) {
			throw new IllegalStateException("培养方案中已存在该课程");
		}
		List<String> prerequisiteIds = command.prerequisiteCourseIds() == null
				? List.of() : command.prerequisiteCourseIds().stream().filter(Objects::nonNull).distinct().toList();
		if (prerequisiteIds.contains(course.getId())) {
			throw new IllegalArgumentException("课程不能将自身设为先修课程");
		}
		if (!prerequisiteIds.isEmpty() && courses.findAllById(prerequisiteIds).size() != prerequisiteIds.size()) {
			throw new IllegalArgumentException("先修课程包含无效数据");
		}
		AcademicProgramCourse item = new AcademicProgramCourse();
		item.setProgramId(programId);
		item.setCourseId(course.getId());
		item.setSuggestedTermNo(command.suggestedTermNo());
		item.setCourseNature(nature);
		item.setCredits(command.credits() == null ? defaultZero(course.getCredits()) : command.credits());
		item.setTotalHours(course.getTotalHours());
		item.setTheoryHours(course.getTheoryHours());
		item.setPracticeHours(course.getPracticeHours());
		item.setPrerequisiteCourseIdsJson(write(prerequisiteIds));
		item.setCourseGroupCode(blankToNull(command.courseGroupCode()));
		item.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
		programCourses.save(item);
		recalculate(program);
		audit.log(actor, "EDU_ACADEMIC_PROGRAM_COURSE_ADD", "programId=" + programId + ",courseId=" + course.getId());
		return view(programs.save(program));
	}

	@Transactional
	public ProgramView submitProgram(String id, String actor) {
		AcademicProgram program = mutableProgram(id, actor);
		validateProgramSubmission(program);
		return view(markProgramSubmitted(program, actor, LocalDateTime.now()));
	}

	@Transactional
	public int submitPrograms(ProgramBatchSubmitCommand command, String actor) {
		assertSchool(actor, command.schoolId());
		List<String> ids = command.programIds();
		if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null || id.isBlank())) {
			throw new IllegalArgumentException("请选择要提交的培养方案");
		}
		// 按固定顺序加锁，先校验整批方案，再写入状态和审计记录。
		List<AcademicProgram> selected = new ArrayList<>();
		for (String id : ids.stream().distinct().sorted().toList()) {
			AcademicProgram program = programs.findForUpdate(id)
					.filter(item -> command.schoolId().equals(item.getSchoolId()))
					.orElseThrow(() -> new IllegalArgumentException("所选培养方案不存在或不属于当前学校，请刷新后重试"));
			if (!"DRAFT".equals(program.getStatus())) {
				throw new IllegalStateException("方案 " + program.getProgramCode() + " 已不处于草稿状态，请刷新后重试");
			}
			validateProgramSubmission(program);
			selected.add(program);
		}
		LocalDateTime submittedAt = LocalDateTime.now();
		selected.forEach(program -> markProgramSubmitted(program, actor, submittedAt));
		return selected.size();
	}

	private void validateProgramSubmission(AcademicProgram program) {
		List<AcademicProgramCourse> items = programCourses.findByProgramIdOrderBySuggestedTermNoAscSortOrderAsc(program.getId());
		String prefix = "方案 " + program.getProgramCode() + "：";
		if (items.isEmpty()) {
			throw new IllegalStateException(prefix + "培养方案至少需要一门课程");
		}
		BigDecimal credits = items.stream().map(AcademicProgramCourse::getCredits)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		if (credits.compareTo(program.getMinimumCredits()) < 0) {
			throw new IllegalStateException(prefix + "课程学分低于毕业最低学分");
		}
	}

	private AcademicProgram markProgramSubmitted(AcademicProgram program, String actor, LocalDateTime submittedAt) {
		program.setStatus("SUBMITTED");
		program.setSubmittedBy(actor);
		program.setSubmittedAt(submittedAt);
		audit.log(actor, "EDU_ACADEMIC_PROGRAM_SUBMIT", "programId=" + program.getId());
		return programs.save(program);
	}

	@Transactional
	public ProgramView publishProgram(String id, String actor) {
		AcademicProgram program = programs.findForUpdate(id)
				.orElseThrow(() -> new IllegalArgumentException("培养方案不存在"));
		assertSchool(actor, program.getSchoolId());
		if (!"SUBMITTED".equals(program.getStatus())) {
			throw new IllegalStateException("只有已提交方案可以发布");
		}
		if (actor.equals(program.getSubmittedBy())) {
			throw new AccessDeniedException("方案提交人不能审核发布自己的方案");
		}
		programs.findFirstBySchoolIdAndMajorIdAndCohortYearAndEducationLevelAndStatus(
				program.getSchoolId(), program.getMajorId(), program.getCohortYear(),
				program.getEducationLevel(), "PUBLISHED").ifPresent(previous -> {
			previous.setStatus("ARCHIVED");
			programs.save(previous);
		});
		String snapshot = write(Map.of("program", snapshotProgram(program), "courses", courseSnapshots(id)));
		program.setSnapshotJson(snapshot);
		program.setSnapshotHash(hash(snapshot));
		program.setStatus("PUBLISHED");
		program.setPublishedBy(actor);
		program.setPublishedAt(LocalDateTime.now());
		audit.log(actor, "EDU_ACADEMIC_PROGRAM_PUBLISH", "programId=" + id + ",hash=" + program.getSnapshotHash());
		return view(programs.save(program));
	}

	@Transactional
	public TermRegistration submitRegistration(RegistrationCommand command, String actor) {
		assertSchool(actor, command.schoolId());
		var scope = dataScopes.resolve(actor);
		dataScopes.assertStudentAccess(scope, command.studentId());
		terms.findByTermCode(command.termCode())
				.filter(term -> command.schoolId().equals(term.getOrganizationId()))
				.orElseThrow(() -> new IllegalArgumentException("所选学校下没有该学期"));
		StudentProfile student = students.findById(command.studentId())
				.orElseThrow(() -> new IllegalArgumentException("学生不存在"));
		dataScopes.assertStudentInSchool(scope, command.schoolId(), student);
		if (!"ACTIVE".equals(student.getEnrollmentStatus())) {
			throw new IllegalStateException("学生当前学籍状态不可注册");
		}
		AcademicProgram program = programs.findById(command.programId())
				.orElseThrow(() -> new IllegalArgumentException("培养方案不存在"));
		if (!"PUBLISHED".equals(program.getStatus()) || !command.schoolId().equals(program.getSchoolId())
				|| !Objects.equals(student.getMajorId(), program.getMajorId())
				|| !Objects.equals(student.getGradeYear(), program.getCohortYear())) {
			throw new IllegalStateException("学生没有适用的已发布培养方案");
		}
		TermRegistration registration = registrations
				.findBySchoolIdAndTermCodeAndStudentId(command.schoolId(), command.termCode(), command.studentId())
				.orElseGet(TermRegistration::new);
		if (registration.getId() != null && !Set.of("REJECTED", "CANCELLED").contains(registration.getStatus())) {
			throw new IllegalStateException("该学生本学期已有注册记录");
		}
		registration.setSchoolId(command.schoolId());
		registration.setTermCode(command.termCode());
		registration.setStudentId(command.studentId());
		registration.setProgramId(command.programId());
		registration.setStatus("SUBMITTED");
		registration.setSubmittedAt(LocalDateTime.now());
		registration.setEligibilitySnapshotJson(write(Map.of(
				"studentNo", student.getStudentNo(), "enrollmentStatus", student.getEnrollmentStatus(),
				"majorId", String.valueOf(student.getMajorId()), "programHash", program.getSnapshotHash())));
		audit.log(actor, "EDU_TERM_REGISTRATION_SUBMIT", "studentId=" + student.getId() + ",term=" + command.termCode());
		return registrations.save(registration);
	}

	@Transactional
	public TermRegistration decideRegistration(String id, RegistrationDecisionCommand command, String actor) {
		TermRegistration registration = registrations.findForUpdate(id)
				.orElseThrow(() -> new IllegalArgumentException("学期注册记录不存在"));
		assertSchool(actor, registration.getSchoolId());
		if (!"SUBMITTED".equals(registration.getStatus())) {
			throw new IllegalStateException("当前注册记录不可审核");
		}
		if (actor.equals(registration.getCreateBy())) {
			throw new AccessDeniedException("注册提交人不能审核自己的记录");
		}
		registration.setConfirmedBy(actor);
		registration.setConfirmedAt(LocalDateTime.now());
		if (command.approved()) {
			registration.setStatus("REGISTERED");
		} else {
			if (command.reason() == null || command.reason().isBlank()) {
				throw new IllegalArgumentException("驳回时必须填写原因");
			}
			registration.setStatus("REJECTED");
			registration.setRejectedReason(command.reason().trim());
		}
		audit.log(actor, "EDU_TERM_REGISTRATION_DECIDE", "registrationId=" + id + ",status=" + registration.getStatus());
		return registrations.save(registration);
	}

	@Transactional(readOnly = true)
	public List<TermRegistration> registrations(String schoolId, String termCode, String actor) {
		assertSchool(actor, schoolId);
		return registrations.findBySchoolIdAndTermCodeOrderByCreateTimeDesc(schoolId, termCode);
	}

	@Transactional
	public CourseEnrollment enroll(EnrollmentCommand command, String actor) {
		assertSchool(actor, command.schoolId());
		var scope = dataScopes.resolve(actor);
		dataScopes.assertStudentAccess(scope, command.studentId());
		dataScopes.assertOfferingAccess(scope, command.offeringId());
		CourseOffering offering = offerings.findById(command.offeringId())
				.orElseThrow(() -> new IllegalArgumentException("教学任务不存在"));
		if (!command.termCode().equals(offering.getSemesterCode()) || !"ACTIVE".equals(offering.getStatus())) {
			throw new IllegalStateException("教学任务不属于当前有效学期");
		}
		if (!registrations.existsByTermCodeAndStudentIdAndStatus(command.termCode(), command.studentId(), "REGISTERED")) {
			throw new IllegalStateException("学生尚未完成本学期注册");
		}
		CourseEnrollment enrollment = enrollments
				.findByTermCodeAndStudentIdAndOfferingId(command.termCode(), command.studentId(), command.offeringId())
				.orElseGet(CourseEnrollment::new);
		if (enrollment.getId() != null && "ENROLLED".equals(enrollment.getStatus())) {
			return enrollment;
		}
		if (command.programCourseId() != null && !command.programCourseId().isBlank()) {
			AcademicProgramCourse requirement = programCourses.findById(command.programCourseId())
					.orElseThrow(() -> new IllegalArgumentException("培养方案课程不存在"));
			CourseCatalog course = courses.findById(requirement.getCourseId())
					.orElseThrow(() -> new IllegalArgumentException("培养方案课程已失效"));
			if (!course.getCourseCode().equals(offering.getCourseCode())) {
				throw new IllegalArgumentException("教学任务与培养方案课程不一致");
			}
			assertPrerequisites(command.studentId(), requirement);
			enrollment.setProgramCourseId(requirement.getId());
		}
		enrollment.setSchoolId(command.schoolId());
		enrollment.setTermCode(command.termCode());
		enrollment.setStudentId(command.studentId());
		enrollment.setOfferingId(command.offeringId());
		enrollment.setEnrollmentSource(normalizeSource(command.source()));
		enrollment.setStatus("ENROLLED");
		enrollment.setEnrolledAt(LocalDateTime.now());
		enrollment.setWithdrawnAt(null);
		enrollment.setWithdrawnReason(null);
		syncMember(enrollment, true);
		audit.log(actor, "EDU_COURSE_ENROLL", "studentId=" + command.studentId() + ",offeringId=" + command.offeringId());
		return enrollments.save(enrollment);
	}

	@Transactional
	public CourseEnrollment withdraw(String id, WithdrawalCommand command, String actor) {
		CourseEnrollment enrollment = enrollments.findForUpdate(id)
				.orElseThrow(() -> new IllegalArgumentException("选课记录不存在"));
		assertSchool(actor, enrollment.getSchoolId());
		if (!"ENROLLED".equals(enrollment.getStatus())) {
			throw new IllegalStateException("当前记录不可退课");
		}
		if (command.reason() == null || command.reason().isBlank()) {
			throw new IllegalArgumentException("退课必须填写原因");
		}
		enrollment.setStatus("WITHDRAWN");
		enrollment.setWithdrawnAt(LocalDateTime.now());
		enrollment.setWithdrawnReason(command.reason().trim());
		syncMember(enrollment, false);
		audit.log(actor, "EDU_COURSE_WITHDRAW", "enrollmentId=" + id);
		return enrollments.save(enrollment);
	}

	@Transactional(readOnly = true)
	public List<CourseEnrollment> enrollments(String schoolId, String termCode, String actor) {
		assertSchool(actor, schoolId);
		return enrollments.findBySchoolIdAndTermCodeOrderByCreateTimeDesc(schoolId, termCode);
	}

	@Transactional
	public GraduationAudit evaluateGraduation(GraduationAuditCommand command, String actor) {
		assertSchool(actor, command.schoolId());
		dataScopes.assertStudentAccess(dataScopes.resolve(actor), command.studentId());
		StudentProfile student = students.findById(command.studentId())
				.orElseThrow(() -> new IllegalArgumentException("学生不存在"));
		AcademicProgram program = programs.findById(command.programId())
				.orElseThrow(() -> new IllegalArgumentException("培养方案不存在"));
		if (!"PUBLISHED".equals(program.getStatus()) || !program.getSchoolId().equals(command.schoolId())) {
			throw new IllegalStateException("只能按本校已发布培养方案审核");
		}
		ReadinessView readiness = readiness(student, program);
		GraduationAudit result = graduationAudits
				.findFirstByStudentIdAndProgramIdAndStatusInOrderByCreateTimeDesc(
						student.getId(), program.getId(), List.of("DRAFT", "READY", "NOT_READY"))
				.orElseGet(GraduationAudit::new);
		String evidence = write(Map.of(
				"student", Map.of("id", student.getId(), "studentNo", student.getStudentNo(), "name", student.getStudentName()),
				"programHash", program.getSnapshotHash(), "readiness", readiness));
		result.setSchoolId(command.schoolId());
		result.setStudentId(student.getId());
		result.setProgramId(program.getId());
		result.setRequiredCredits(readiness.requiredCredits());
		result.setEarnedCredits(readiness.earnedCredits());
		result.setUnmetRequirementsJson(write(readiness.unmetRequirements()));
		result.setEvidenceSnapshotJson(evidence);
		result.setSnapshotHash(hash(evidence));
		result.setStatus(readiness.ready() ? "READY" : "NOT_READY");
		result.setReviewedBy(actor);
		result.setReviewedAt(LocalDateTime.now());
		audit.log(actor, "EDU_GRADUATION_AUDIT_EVALUATE", "studentId=" + student.getId() + ",status=" + result.getStatus());
		return graduationAudits.save(result);
	}

	@Transactional
	public GraduationAudit decideGraduation(String id, GraduationDecisionCommand command, String actor) {
		GraduationAudit result = graduationAudits.findForUpdate(id)
				.orElseThrow(() -> new IllegalArgumentException("毕业审核记录不存在"));
		assertSchool(actor, result.getSchoolId());
		if (!Set.of("READY", "NOT_READY").contains(result.getStatus())) {
			throw new IllegalStateException("当前记录不可审批");
		}
		if (actor.equals(result.getReviewedBy())) {
			throw new AccessDeniedException("资格核验人与批准人必须分离");
		}
		if (command.approved() && !"READY".equals(result.getStatus())) {
			throw new IllegalStateException("未满足培养方案要求，不能批准毕业");
		}
		if (!command.approved() && (command.reason() == null || command.reason().isBlank())) {
			throw new IllegalArgumentException("驳回时必须填写原因");
		}
		result.setStatus(command.approved() ? "APPROVED" : "REJECTED");
		result.setApprovedBy(actor);
		result.setApprovedAt(LocalDateTime.now());
		audit.log(actor, "EDU_GRADUATION_AUDIT_DECIDE", "auditId=" + id + ",status=" + result.getStatus());
		return graduationAudits.save(result);
	}

	@Transactional(readOnly = true)
	public List<GraduationAudit> graduationAudits(String schoolId, String actor) {
		assertSchool(actor, schoolId);
		return graduationAudits.findBySchoolIdOrderByCreateTimeDesc(schoolId);
	}

	private ReadinessView readiness(StudentProfile student, AcademicProgram program) {
		List<AcademicProgramCourse> requirements = programCourses
				.findByProgramIdOrderBySuggestedTermNoAscSortOrderAsc(program.getId());
		Map<String, CourseCatalog> catalogs = courses.findAllById(requirements.stream()
				.map(AcademicProgramCourse::getCourseId).toList()).stream()
				.collect(Collectors.toMap(CourseCatalog::getId, Function.identity()));
		Set<String> passedCourseCodes = passedCourseCodes(student.getId());
		BigDecimal earned = BigDecimal.ZERO;
		List<Map<String, Object>> unmet = new ArrayList<>();
		for (AcademicProgramCourse requirement : requirements) {
			CourseCatalog course = catalogs.get(requirement.getCourseId());
			boolean passed = course != null && passedCourseCodes.contains(course.getCourseCode());
			if (passed) {
				earned = earned.add(requirement.getCredits());
			} else if ("REQUIRED".equals(requirement.getCourseNature())) {
				unmet.add(Map.of(
						"courseId", requirement.getCourseId(),
						"courseCode", course == null ? "" : course.getCourseCode(),
						"courseName", course == null ? "课程已失效" : course.getCourseName(),
						"reason", "必修课程尚未通过"));
			}
		}
		if (earned.compareTo(program.getMinimumCredits()) < 0) {
			unmet.add(Map.of("reason", "已获学分低于毕业最低学分", "missingCredits",
					program.getMinimumCredits().subtract(earned)));
		}
		return new ReadinessView(student.getId(), student.getStudentNo(), student.getStudentName(),
				program.getMinimumCredits(), earned, unmet, unmet.isEmpty());
	}

	private Set<String> passedCourseCodes(String studentId) {
		Map<String, CourseGrade> latestByGradebook = new HashMap<>();
		for (CourseGrade grade : courseGrades.findByStudentIdOrderByVersionNoDesc(studentId)) {
			latestByGradebook.putIfAbsent(grade.getGradebookId(), grade);
		}
		Set<String> result = new HashSet<>();
		for (CourseGrade grade : latestByGradebook.values()) {
			if (!Boolean.TRUE.equals(grade.getPassed())) {
				continue;
			}
			gradebooks.findById(grade.getGradebookId())
					.filter(book -> "PUBLISHED".equals(book.getStatus()))
					.flatMap(book -> offerings.findById(book.getOfferingId()))
					.map(CourseOffering::getCourseCode)
					.ifPresent(result::add);
		}
		return result;
	}

	private void assertPrerequisites(String studentId, AcademicProgramCourse requirement) {
		List<String> prerequisiteIds = readIds(requirement.getPrerequisiteCourseIdsJson());
		if (prerequisiteIds.isEmpty()) {
			return;
		}
		Set<String> passed = passedCourseCodes(studentId);
		List<CourseCatalog> missing = courses.findAllById(prerequisiteIds).stream()
				.filter(course -> !passed.contains(course.getCourseCode())).toList();
		if (!missing.isEmpty()) {
			throw new IllegalStateException("尚未通过先修课程：" + missing.stream()
					.map(CourseCatalog::getCourseName).collect(Collectors.joining("、")));
		}
	}

	private void syncMember(CourseEnrollment enrollment, boolean active) {
		TeachingClassMember member = members.findByOfferingIdAndStudentId(
				enrollment.getOfferingId(), enrollment.getStudentId()).orElseGet(TeachingClassMember::new);
		member.setOfferingId(enrollment.getOfferingId());
		member.setStudentId(enrollment.getStudentId());
		// The enrollment ledger keeps the detailed origin (manual/import/agent).
		// Teaching-class membership is an execution projection whose existing
		// database contract only distinguishes manual and source-class members.
		member.setEnrollmentSource("MANUAL");
		if (active) {
			member.setEnrollmentStatus("ENROLLED");
			member.setEnrolledAt(LocalDateTime.now());
			member.setWithdrawnAt(null);
		} else {
			member.setEnrollmentStatus("WITHDRAWN");
			member.setWithdrawnAt(LocalDateTime.now());
		}
		members.save(member);
	}

	private AcademicProgram mutableProgram(String id, String actor) {
		AcademicProgram program = programs.findForUpdate(id)
				.orElseThrow(() -> new IllegalArgumentException("培养方案不存在"));
		assertSchool(actor, program.getSchoolId());
		if (!"DRAFT".equals(program.getStatus())) {
			throw new IllegalStateException("只有草稿培养方案可以修改");
		}
		return program;
	}

	private void recalculate(AcademicProgram program) {
		program.setTotalHours(programCourses.findByProgramIdOrderBySuggestedTermNoAscSortOrderAsc(program.getId())
				.stream().mapToInt(AcademicProgramCourse::getTotalHours).sum());
	}

	private ProgramView view(AcademicProgram program) {
		Map<String, CourseCatalog> catalogs = courses.findAllById(programCourses
				.findByProgramIdOrderBySuggestedTermNoAscSortOrderAsc(program.getId()).stream()
				.map(AcademicProgramCourse::getCourseId).toList()).stream()
				.collect(Collectors.toMap(CourseCatalog::getId, Function.identity()));
		List<ProgramCourseView> items = programCourses
				.findByProgramIdOrderBySuggestedTermNoAscSortOrderAsc(program.getId()).stream()
				.map(item -> {
					CourseCatalog course = catalogs.get(item.getCourseId());
					return new ProgramCourseView(item.getId(), item.getCourseId(),
							course == null ? "" : course.getCourseCode(),
							course == null ? "课程已失效" : course.getCourseName(),
							item.getSuggestedTermNo(), item.getCourseNature(), item.getCredits(),
							item.getTotalHours(), item.getTheoryHours(), item.getPracticeHours(),
							readIds(item.getPrerequisiteCourseIdsJson()), item.getCourseGroupCode(), item.getSortOrder());
				}).toList();
		return new ProgramView(program.getId(), program.getSchoolId(), program.getMajorId(),
				program.getCohortYear(), program.getEducationLevel(), program.getProgramCode(),
				program.getProgramName(), program.getVersionNo(), program.getMinimumCredits(),
				program.getTotalHours(), program.getStatus(), program.getRowVersion(),
				program.getEffectiveStartDate(), program.getEffectiveEndDate(), items);
	}

	private Map<String, Object> snapshotProgram(AcademicProgram program) {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("id", program.getId());
		value.put("schoolId", program.getSchoolId());
		value.put("majorId", program.getMajorId());
		value.put("cohortYear", program.getCohortYear());
		value.put("educationLevel", program.getEducationLevel());
		value.put("programCode", program.getProgramCode());
		value.put("programName", program.getProgramName());
		value.put("versionNo", program.getVersionNo());
		value.put("minimumCredits", program.getMinimumCredits());
		value.put("totalHours", program.getTotalHours());
		return value;
	}

	private List<Map<String, Object>> courseSnapshots(String programId) {
		return programCourses.findByProgramIdOrderBySuggestedTermNoAscSortOrderAsc(programId).stream()
				.map(item -> {
					Map<String, Object> value = new LinkedHashMap<>();
					value.put("id", item.getId());
					value.put("courseId", item.getCourseId());
					value.put("suggestedTermNo", item.getSuggestedTermNo());
					value.put("courseNature", item.getCourseNature());
					value.put("credits", item.getCredits());
					value.put("totalHours", item.getTotalHours());
					value.put("prerequisiteCourseIds", readIds(item.getPrerequisiteCourseIdsJson()));
					return value;
				}).toList();
	}

	private void validateProgram(ProgramCommand command) {
		if (command.schoolId() == null || command.schoolId().isBlank()
				|| command.majorId() == null || command.majorId().isBlank()
				|| command.programCode() == null || command.programCode().isBlank()
				|| command.programName() == null || command.programName().isBlank()) {
			throw new IllegalArgumentException("学校、专业、方案编码和名称不能为空");
		}
		if (command.programCode().trim().length() > 64 || command.programName().trim().length() > 200) {
			throw new IllegalArgumentException("方案编码最多 64 个字符，方案名称最多 200 个字符");
		}
		if (command.cohortYear() == null || command.cohortYear() < 2000) {
			throw new IllegalArgumentException("适用入学年份无效");
		}
		if (!EDUCATION_LEVELS.contains(normalize(command.educationLevel()))) {
			throw new IllegalArgumentException("培养层次不支持");
		}
		if (command.minimumCredits() == null || command.minimumCredits().signum() < 0
				|| command.minimumCredits().compareTo(new BigDecimal("999999.99")) > 0
				|| command.minimumCredits().stripTrailingZeros().scale() > 2) {
			throw new IllegalArgumentException("最低毕业学分无效");
		}
		if (command.effectiveStartDate() != null && command.effectiveEndDate() != null
				&& command.effectiveEndDate().isBefore(command.effectiveStartDate())) {
			throw new IllegalArgumentException("失效日期不能早于生效日期");
		}
	}

	private void assertSchool(String actor, String schoolId) {
		if (schoolId == null || schoolId.isBlank()) {
			throw new IllegalArgumentException("请选择学校");
		}
		dataScopes.assertSchoolAccess(dataScopes.resolve(actor), schoolId);
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
	}

	private String normalizeSource(String source) {
		String value = normalize(source);
		return Set.of("MANUAL", "BATCH", "AGENT", "IMPORT").contains(value) ? value : "MANUAL";
	}

	private BigDecimal defaultZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private String write(Object value) {
		try {
			return json.writeValueAsString(value);
		} catch (Exception exception) {
			throw new IllegalStateException("业务快照序列化失败", exception);
		}
	}

	private List<String> readIds(String value) {
		try {
			return value == null || value.isBlank() ? List.of()
					: json.readValue(value, new TypeReference<List<String>>() { });
		} catch (Exception exception) {
			throw new IllegalStateException("先修课程数据损坏", exception);
		}
	}

	private String hash(String value) {
		try {
			return java.util.HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception exception) {
			throw new IllegalStateException("业务快照摘要生成失败", exception);
		}
	}
}
