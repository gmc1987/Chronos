package com.chronos.education.operationagent.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import com.chronos.Idao.IEmployeeRepository;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.AdmissionCandidateRepository;
import com.chronos.education.scheduling.dao.CourseCatalogRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.SubjectRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.dao.TeacherTeachingAssignmentRepository;
import com.chronos.education.scheduling.model.AutoScheduleCommand;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleCandidateView;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.chronos.education.scheduling.model.dto.AdmissionDtos.EnrollmentCommand;
import com.chronos.education.scheduling.model.dto.AdmissionDtos.ReviewCommand;
import com.chronos.education.scheduling.model.dto.StudentStatusChangeCommand;
import com.chronos.education.scheduling.service.AcademicDataService;
import com.chronos.education.scheduling.service.AccommodationService;
import com.chronos.education.scheduling.service.AccommodationImportService;
import com.chronos.education.scheduling.service.AdmissionImportService;
import com.chronos.education.scheduling.service.AdmissionService;
import com.chronos.education.scheduling.service.AutoSchedulingService;
import com.chronos.education.scheduling.service.ClassSchedulingService;
import com.chronos.education.scheduling.service.SchedulePlanVersionService;
import com.chronos.education.scheduling.service.SemesterPreparationExecutionService;
import com.chronos.education.scheduling.service.StudentAccountProvisioningService;
import com.chronos.education.scheduling.service.StudentStatusChangeService;
import com.chronos.file.service.ManagedFileService;
import com.chronos.knowledge.service.KnowledgeService;

/**
 * 学期运营智能体的受控工具边界。所有写入都复用现有领域服务，禁止模型生成 SQL
 * 或绕开领域校验直接修改正式业务表。
 */
@Service
public class OperationAgentBusinessTools {
	private final JdbcTemplate jdbc;
	private final AdmissionService admissions;
	private final AdmissionImportService admissionImports;
	private final ManagedFileService managedFiles;
	private final KnowledgeService knowledge;
	private final AdmissionCandidateRepository admissionCandidates;
	private final AdministrativeClassRepository administrativeClasses;
	private final StudentProfileRepository students;
	private final StudentAccountProvisioningService studentAccounts;
	private final EducationUserBindingRepository bindings;
	private final IEmployeeRepository employees;
	private final TeacherAcademicProfileRepository teachers;
	private final AcademicDataService academicData;
	private final TeacherTeachingAssignmentRepository teachingAssignments;
	private final CourseCatalogRepository courses;
	private final CourseOfferingRepository offerings;
	private final AcademicTermRepository terms;
	private final ClassSchedulingService scheduling;
	private final AutoSchedulingService autoScheduling;
	private final SchedulePlanVersionService scheduleVersions;
	private final StudentStatusChangeService studentChanges;
	private final SemesterPreparationExecutionService preparationExecutions;
	private final SubjectRepository subjects;
	private final AccommodationService accommodations;
	private final AccommodationImportService accommodationImports;

	public OperationAgentBusinessTools(
			JdbcTemplate jdbc,
			AdmissionService admissions,
			AdmissionImportService admissionImports,
			ManagedFileService managedFiles,
			KnowledgeService knowledge,
			AdmissionCandidateRepository admissionCandidates,
			AdministrativeClassRepository administrativeClasses,
			StudentProfileRepository students,
			StudentAccountProvisioningService studentAccounts,
			EducationUserBindingRepository bindings,
			IEmployeeRepository employees,
			TeacherAcademicProfileRepository teachers,
			AcademicDataService academicData,
			TeacherTeachingAssignmentRepository teachingAssignments,
			CourseCatalogRepository courses,
			CourseOfferingRepository offerings,
			AcademicTermRepository terms,
			ClassSchedulingService scheduling,
			AutoSchedulingService autoScheduling,
			SchedulePlanVersionService scheduleVersions,
			StudentStatusChangeService studentChanges,
			SemesterPreparationExecutionService preparationExecutions,
			SubjectRepository subjects,
			AccommodationService accommodations,
			AccommodationImportService accommodationImports) {
		this.jdbc = jdbc;
		this.admissions = admissions;
		this.admissionImports = admissionImports;
		this.managedFiles = managedFiles;
		this.knowledge = knowledge;
		this.admissionCandidates = admissionCandidates;
		this.administrativeClasses = administrativeClasses;
		this.students = students;
		this.studentAccounts = studentAccounts;
		this.bindings = bindings;
		this.employees = employees;
		this.teachers = teachers;
		this.academicData = academicData;
		this.teachingAssignments = teachingAssignments;
		this.courses = courses;
		this.offerings = offerings;
		this.terms = terms;
		this.scheduling = scheduling;
		this.autoScheduling = autoScheduling;
		this.scheduleVersions = scheduleVersions;
		this.studentChanges = studentChanges;
		this.preparationExecutions = preparationExecutions;
		this.subjects = subjects;
		this.accommodations = accommodations;
		this.accommodationImports = accommodationImports;
	}

	public Map<String, Object> execute(
			String toolCode,
			Map<String, Object> config,
			String knowledgeBaseId,
			String objective,
			String actor) {
		return switch (toolCode) {
			case "semester.context.check" -> semesterContext(config);
			case "knowledge.policy.search" -> knowledgePolicy(config, knowledgeBaseId, objective, actor);
			case "admission.import.managed-file" -> importAdmissions(config, actor);
			case "admission.data.check" -> admissionCheck(config);
			case "admission.class.assign.batch" -> assignAdmissionClasses(config);
			case "admission.review.batch" -> reviewAdmissions(config, actor);
			case "admission.enroll.batch" -> enrollAdmissions(config);
			case "account.binding.check" -> accountBindingCheck();
			case "admission.accommodation.check" -> accommodationCheck(config);
			case "admission.accommodation.resource.check" -> accommodationResourceCheck();
			case "admission.accommodation.resource.maintain" -> accommodationResourceMaintain(config);
			case "admission.accommodation.plan" -> accommodationPlan(config);
			case "admission.accommodation.assign.batch" -> accommodationAssign(config, actor);
			case "student.account.provision.batch" -> provisionStudentAccounts(config);
			case "teacher.onboarding.batch" -> onboardTeachers(config);
			case "teaching.assignment.generate" -> generateTeachingAssignments(config);
			case "teaching.offering.generate" -> generateTeachingOfferings(config);
			case "teaching.readiness.check" -> teachingReadiness(config);
			case "scheduling.generate.full" -> generateFullSchedule(config, actor);
			case "scheduling.submit.best" -> submitBestSchedule(config, actor);
			case "scheduling.review.submitted" -> reviewSubmittedSchedule(config, actor);
			case "scheduling.apply.approved" -> applyApprovedSchedule(config, actor);
			case "scheduling.publish" -> publishSchedule(config, actor);
			case "term.end.check" -> termEndCheck(config);
			case "term.end.exam.check" -> termEndExamCheck(config);
			case "term.end.grade.check" -> termEndGradeCheck(config);
			case "term.graduation.prepare" -> prepareGraduation(config, actor);
			case "term.end.archive.check" -> termEndArchiveCheck(config);
			default -> throw new IllegalArgumentException("未注册的业务工具：" + toolCode);
		};
	}

	private Map<String, Object> semesterContext(Map<String, Object> config) {
		String semesterCode = text(config.get("semesterCode"));
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("semesterCode", semesterCode);
		result.put("courseOfferingCount", count(
				semesterCode.isBlank()
						? "select count(*) from edu_course_offering"
						: "select count(*) from edu_course_offering where semester_code = ?",
				semesterCode));
		result.put("activeClassCount", count("select count(*) from edu_administrative_class where status='ACTIVE'"));
		result.put("activeStudentCount", count("select count(*) from edu_student_profile where enrollment_status='ACTIVE'"));
		result.put("activeTeacherCount", count("select count(*) from edu_teacher_profile where enabled=true and employment_status='ACTIVE'"));
		return result;
	}

	private Map<String, Object> knowledgePolicy(
			Map<String, Object> config,
			String knowledgeBaseId,
			String objective,
			String actor) {
		if (knowledgeBaseId == null || knowledgeBaseId.isBlank()) {
			return Map.of("referenceCount", 0, "message", "计划未绑定知识库，本步骤未引用校内政策");
		}
		String query = text(config.getOrDefault("query", objective));
		var authentication = new UsernamePasswordAuthenticationToken(actor, "", List.of());
		var answer = knowledge.ask(knowledgeBaseId, query, authentication);
		return Map.of(
				"referenceCount", answer.references().size(),
				"query", query,
				"answer", answer.answer(),
				"modelGenerated", answer.modelGenerated(),
				"references", answer.references());
	}

	private Map<String, Object> importAdmissions(Map<String, Object> config, String actor) {
		String fileId = required(config.get("managedFileId"), "录取名册文件 ID");
		boolean dryRun = bool(config.get("dryRun"), true);
		var authentication = new UsernamePasswordAuthenticationToken(actor, "", List.of());
		var file = managedFiles.read(fileId, authentication);
		if (!file.filename().toLowerCase().endsWith(".xlsx")) {
			throw new IllegalArgumentException("录取名册必须是 xlsx 文件");
		}
		var result = admissionImports.importBytes(file.content(), dryRun);
		if (!result.errors().isEmpty()) {
			throw new IllegalArgumentException("新生名册校验失败（" + result.errors().size() + " 行）："
					+ String.join("；", result.errors()));
		}
		return Map.of(
				"managedFileId", fileId,
				"filename", file.filename(),
				"dryRun", dryRun,
				"totalRows", result.totalCount(),
				"importedRows", result.successCount(),
				"errors", result.errors(),
				"rows", result.rows());
	}

	private Map<String, Object> admissionCheck(Map<String, Object> config) {
		int year = number(config.get("admissionYear"), Year.now().getValue());
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("admissionYear", year);
		result.put("total", count("select count(*) from edu_admission_candidate where admission_year=?", year));
		result.put("admitted", count("select count(*) from edu_admission_candidate where admission_year=? and admission_status='ADMITTED'", year));
		result.put("verified", count("select count(*) from edu_admission_candidate where admission_year=? and admission_status='VERIFIED'", year));
		result.put("enrolled", count("select count(*) from edu_admission_candidate where admission_year=? and admission_status='ENROLLED'", year));
		result.put("missingMajor", count("select count(*) from edu_admission_candidate where admission_year=? and admitted_major_id is null", year));
		result.put("missingPlannedClass", count("select count(*) from edu_admission_candidate where admission_year=? and planned_class_id is null", year));
		return result;
	}

	/** 只读返回住宿容量和未分配清单，正式分配仍由住宿管理人工确认。 */
	private Map<String, Object> accommodationCheck(Map<String, Object> config) {
		String semester = required(config.get("semesterCode"), "学期编码");
		int year = number(config.get("admissionYear"), Year.now().getValue());
		long students = count("select count(*) from edu_student_profile where enrollment_status='ACTIVE' and grade_year=?", year);
		long assigned = count("select count(*) from edu_student_accommodation where semester_code=? and status='ACTIVE'", semester);
		long beds = count("select count(*) from edu_dorm_bed where status='AVAILABLE'");
		List<Map<String, Object>> unassigned = jdbc.queryForList("""
				select student.id, student.student_no, student.student_name
				from edu_student_profile student
				where student.enrollment_status='ACTIVE' and student.grade_year=?
				  and not exists (select 1 from edu_student_accommodation assignment
				                  where assignment.student_id=student.id and assignment.semester_code=? and assignment.status='ACTIVE')
				order by student.student_no limit 100
				""", year, semester);
		return Map.of("semesterCode", semester, "admissionYear", year, "activeStudents", students,
				"assignedStudents", assigned, "unassignedStudents", Math.max(0, students - assigned),
				"availableBeds", beds, "capacityEnough", beds >= Math.max(0, students - assigned),
				"manualEntryRoute", "/admin/education/accommodations", "unassignedPreview", unassigned);
	}

	private Map<String, Object> accommodationResourceCheck() {
		List<Map<String, Object>> rooms = jdbc.queryForList("""
				select building.building_code as "buildingCode", building.building_name as "buildingName",
				       room.room_no as "roomNo", room.floor_no as "floorNo", room.capacity,
				       count(bed.id) as "bedCount", room.capacity - count(bed.id) as "missingBedCount"
				from edu_dorm_room room
				join edu_dorm_building building on building.id = room.building_id
				left join edu_dorm_bed bed on bed.room_id = room.id
				group by building.building_code, building.building_name, room.room_no, room.floor_no, room.capacity
				having count(bed.id) <> room.capacity
				order by building.building_code, room.floor_no, room.room_no
				""");
		return Map.of("valid", rooms.isEmpty(), "issueCount", rooms.size(), "issues", rooms,
				"manualEntryRoute", "/admin/education/accommodations",
				"message", rooms.isEmpty() ? "宿舍房间容量与床位数据一致" : "发现房间容量与床位数量不一致，可执行资源维护补齐床位");
	}

	private Map<String, Object> accommodationResourceMaintain(Map<String, Object> config) {
		Object value = config.get("resources");
		if (!(value instanceof List<?> values) || values.isEmpty()) throw new IllegalArgumentException("宿舍资源维护必须提供 resources 候选");
		List<Map<?, ?>> rows = new java.util.ArrayList<>();
		for (Object item : values) {
			if (!(item instanceof Map<?, ?> row)) throw new IllegalArgumentException("宿舍资源候选格式不正确");
			rows.add(row);
		}
		int count = accommodationImports.maintainRows(rows, bool(config.get("dryRun"), false));
		return Map.of("dryRun", bool(config.get("dryRun"), false), "processedCount", count,
				"requiresConfirmation", true, "manualEntryRoute", "/admin/education/accommodations");
	}

	private Map<String, Object> accommodationPlan(Map<String, Object> config) {
		String semester = required(config.get("semesterCode"), "学期编码");
		int year = number(config.get("admissionYear"), Year.now().getValue());
		int limit = bounded(config.get("batchLimit"), 100, 500);
		List<Map<String, Object>> students = jdbc.queryForList("""
				select student.id student_id, student.student_no, student.student_name
				from edu_student_profile student
				where student.enrollment_status='ACTIVE' and student.grade_year=?
				  and not exists (select 1 from edu_student_accommodation assignment
				                  where assignment.student_id=student.id and assignment.semester_code=? and assignment.status='ACTIVE')
				order by student.student_no limit ?
				""", year, semester, limit);
		List<Map<String, Object>> beds = jdbc.queryForList("""
				select bed.id bed_id, building.building_name, room.room_no, bed.bed_no
				from edu_dorm_bed bed
				join edu_dorm_room room on room.id=bed.room_id
				join edu_dorm_building building on building.id=room.building_id
				where bed.status='AVAILABLE'
				  and not exists (select 1 from edu_student_accommodation assignment
				                  where assignment.bed_id=bed.id and assignment.semester_code=? and assignment.status='ACTIVE')
				order by building.building_code, room.floor_no, room.room_no, bed.bed_no limit ?
				""", semester, limit);
		List<Map<String, Object>> assignments = new java.util.ArrayList<>();
		int pairs = Math.min(students.size(), beds.size());
		for (int index = 0; index < pairs; index++) {
			Map<String, Object> student = students.get(index);
			Map<String, Object> bed = beds.get(index);
			Map<String, Object> pair = new LinkedHashMap<>();
			pair.put("studentId", student.get("student_id"));
			pair.put("studentNo", student.get("student_no"));
			pair.put("studentName", student.get("student_name"));
			pair.put("bedId", bed.get("bed_id"));
			pair.put("bedLabel", bed.get("building_name") + " / " + bed.get("room_no") + " / " + bed.get("bed_no"));
			assignments.add(pair);
		}
		return Map.of("semesterCode", semester, "admissionYear", year, "candidateCount", assignments.size(),
				"unassignedStudentCount", students.size() - pairs, "availableBedCount", beds.size() - pairs,
				"assignments", assignments, "requiresConfirmation", true,
				"manualEntryRoute", "/admin/education/accommodations");
	}

	private Map<String, Object> accommodationAssign(Map<String, Object> config, String actor) {
		String semester = required(config.get("semesterCode"), "学期编码");
		Object value = config.get("assignments");
		if (!(value instanceof List<?> values) || values.isEmpty()) throw new IllegalArgumentException("住宿分配必须提供已确认的 assignments 候选");
		if (bool(config.get("dryRun"), false)) return Map.of("semesterCode", semester, "dryRun", true, "candidateCount", values.size(), "message", "候选模式未写入正式住宿关系");
		int assigned = 0;
		for (Object item : values) {
			if (!(item instanceof Map<?, ?> pair)) throw new IllegalArgumentException("住宿分配候选格式不正确");
			String studentId = text(pair.get("studentId"));
			String bedId = text(pair.get("bedId"));
			accommodations.assign(new AccommodationService.AssignmentCommand(semester, studentId, bedId), actor);
			assigned++;
		}
		return Map.of("semesterCode", semester, "assignedCount", assigned, "candidateCount", values.size());
	}

	private Map<String, Object> assignAdmissionClasses(Map<String, Object> config) {
		int year = number(config.get("admissionYear"), Year.now().getValue());
		int limit = bounded(config.get("batchLimit"), 100, 500);
		int targetSize = bounded(config.get("targetClassSize"), 50, 200);
		return preparationExecutions.assignClasses(year, limit, targetSize, bool(config.get("dryRun"), false));
	}

	private Map<String, Object> reviewAdmissions(Map<String, Object> config, String actor) {
		int year = number(config.get("admissionYear"), Year.now().getValue());
		int limit = bounded(config.get("batchLimit"), 100, 500);
		return preparationExecutions.reviewAdmissions(year, limit, bool(config.get("dryRun"), false), actor);
	}

	private Map<String, Object> enrollAdmissions(Map<String, Object> config) {
		int year = number(config.get("admissionYear"), Year.now().getValue());
		int limit = bounded(config.get("batchLimit"), 50, 200);
		return preparationExecutions.enrollAdmissions(year, limit, bool(config.get("dryRun"), false));
	}

	private Map<String, Object> accountBindingCheck() {
		long activeStudents = count("select count(*) from edu_student_profile where enrollment_status='ACTIVE'");
		long studentBindings = count("select count(distinct profile_id) from edu_user_profile_binding where profile_type='STUDENT' and status='ACTIVE'");
		long activeTeachers = count("select count(*) from edu_teacher_profile where enabled=true and employment_status='ACTIVE'");
		long teacherBindings = count("select count(distinct profile_id) from edu_user_profile_binding where profile_type='TEACHER' and status='ACTIVE'");
		return Map.of("activeStudents", activeStudents, "boundStudentAccounts", studentBindings,
				"missingStudentBindings", Math.max(0, activeStudents - studentBindings),
				"activeTeachers", activeTeachers, "boundTeacherAccounts", teacherBindings,
				"missingTeacherBindings", Math.max(0, activeTeachers - teacherBindings));
	}

	private Map<String, Object> provisionStudentAccounts(Map<String, Object> config) {
		int limit = bounded(config.get("batchLimit"), 100, 500);
		boolean dryRun = bool(config.get("dryRun"), false);
		var missing = students.findAllByOrderByStudentNo().stream()
				.filter(value -> "ACTIVE".equals(value.getEnrollmentStatus()))
				.filter(value -> bindings.findByProfileTypeAndProfileIdAndStatus("STUDENT", value.getId(), "ACTIVE").isEmpty())
				.limit(limit).toList();
		int created = 0;
		for (var student : missing) {
			String campusId = administrativeClasses.findById(student.getAdministrativeClassId())
					.map(value -> value.getCampusId()).orElse(null);
			if (!dryRun) {
				studentAccounts.provision(student, campusId);
				created++;
			}
		}
		return Map.of("missingFound", missing.size(), "provisioned", created, "batchLimit", limit, "dryRun", dryRun);
	}

	private Map<String, Object> onboardTeachers(Map<String, Object> config) {
		int limit = bounded(config.get("batchLimit"), 50, 200);
		boolean dryRun = bool(config.get("dryRun"), false);
		Set<String> requested = stringSet(config.get("employeeIds"));
		var eligible = employees.findAll().stream()
				.filter(value -> "ACTIVE".equalsIgnoreCase(value.getEmploymentStatus()))
				.filter(value -> requested.isEmpty()
						? Set.of("TEACHER", "FACULTY").contains(text(value.getEmployeeType()).toUpperCase())
						: requested.contains(value.getId()) || requested.contains(value.getEmployeeCode()))
				.sorted(Comparator.comparing(value -> value.getEmployeeCode()))
				.limit(limit).toList();
		int profileCreated = 0;
		int accountProvisioned = 0;
		for (var employee : eligible) {
			var profile = teachers.findByEmployeeId(employee.getId()).orElse(null);
			if (profile == null) {
				if (dryRun) {
					profileCreated++;
					accountProvisioned++;
					continue;
				}
				TeacherAcademicProfile command = new TeacherAcademicProfile();
				command.setEmployeeId(employee.getId());
				command.setSpecialty(text(config.getOrDefault("defaultSpecialty", "待完善")));
				command.setMaxWeeklyLessons(number(config.get("maxWeeklyLessons"), 20));
				command.setMaxDailyLessons(number(config.get("maxDailyLessons"), 6));
				command.setMaxConsecutiveLessons(number(config.get("maxConsecutiveLessons"), 4));
				profile = academicData.saveTeacher(null, command);
				profileCreated++;
			}
			if (bindings.findByProfileTypeAndProfileIdAndStatus("TEACHER", profile.getId(), "ACTIVE").isEmpty()) {
				if (!dryRun) academicData.bindTeacherAccount(profile.getId());
				accountProvisioned++;
			}
		}
		return Map.of("eligibleEmployees", eligible.size(), "profileCreated", profileCreated,
				"accountProvisioned", accountProvisioned, "batchLimit", limit, "dryRun", dryRun);
	}

	private Map<String, Object> generateTeachingOfferings(Map<String, Object> config) {
		String semesterCode = required(config.get("semesterCode"), "学期编码");
		int limit = bounded(config.get("batchLimit"), 100, 500);
		return preparationExecutions.generateOfferings(semesterCode, limit, bool(config.get("dryRun"), false));
	}

	/**
	 * 根据用户确认的工号、学科编码和班级编码批量建立教师分工。
	 * 全批次先解析验证再写入，避免配置中途出错造成半批数据。
	 */
	private Map<String, Object> generateTeachingAssignments(Map<String, Object> config) {
		String semesterCode = required(config.get("semesterCode"), "学期编码");
		boolean dryRun = bool(config.get("dryRun"), false);
		var term = terms.findByTermCode(semesterCode)
				.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		Object raw = config.get("assignments");
		if (!(raw instanceof Iterable<?> iterable)) {
			throw new IllegalArgumentException("教师分工必须提供 assignments 数组");
		}
		List<TeachingAssignmentCandidate> candidates = new ArrayList<>();
		for (Object item : iterable) {
			if (!(item instanceof Map<?, ?> source)) throw new IllegalArgumentException("教师分工项必须是对象");
			String teacherNo = required(source.get("teacherNo"), "教师工号");
			String subjectCode = required(source.get("subjectCode"), "学科编码");
			String classCode = required(source.get("classCode"), "行政班编码");
			var teacher = teachers.findByTeacherNo(teacherNo)
					.filter(value -> Boolean.TRUE.equals(value.getEnabled()) && "ACTIVE".equals(value.getEmploymentStatus()))
					.orElseThrow(() -> new IllegalArgumentException("教师不存在或未启用：" + teacherNo));
			var subject = subjects.findBySubjectCode(subjectCode)
					.filter(value -> Boolean.TRUE.equals(value.getEnabled()))
					.orElseThrow(() -> new IllegalArgumentException("学科不存在或未启用：" + subjectCode));
			var administrativeClass = administrativeClasses.findByClassCode(classCode)
					.filter(value -> "ACTIVE".equals(value.getStatus()))
					.orElseThrow(() -> new IllegalArgumentException("行政班不存在或未启用：" + classCode));
			int weeklyLessons = Math.max(1, number(source.get("weeklyLessons"), 2));
			candidates.add(new TeachingAssignmentCandidate(teacher, subject, administrativeClass, weeklyLessons));
		}
		if (candidates.isEmpty()) throw new IllegalArgumentException("教师分工数组不能为空");
		var existing = teachingAssignments.findAllByOrderByCreateTimeDesc().stream()
				.filter(value -> term.getId().equals(value.getAcademicTermId())).toList();
		int created = 0;
		int skipped = 0;
		for (TeachingAssignmentCandidate candidate : candidates) {
			boolean duplicate = existing.stream().anyMatch(value ->
					candidate.teacher().getId().equals(value.getTeacherId())
							&& candidate.subject().getId().equals(value.getSubjectId())
							&& candidate.administrativeClass().getId().equals(value.getAdministrativeClassId()));
			if (duplicate) {
				skipped++;
				continue;
			}
			var command = new com.chronos.education.scheduling.model.TeacherTeachingAssignment();
			command.setAcademicTermId(term.getId());
			command.setTeacherId(candidate.teacher().getId());
			command.setSubjectId(candidate.subject().getId());
			command.setGradeId(candidate.administrativeClass().getGradeId());
			command.setAdministrativeClassId(candidate.administrativeClass().getId());
			command.setAssignmentRole("TEACHER");
			command.setWeeklyLessons(candidate.weeklyLessons());
			command.setEnabled(true);
			if (!dryRun) {
				academicData.saveTeachingAssignment(null, command);
				created++;
			}
		}
		return Map.of("semesterCode", semesterCode, "candidateCount", candidates.size(),
				"created", created, "skippedExisting", skipped, "dryRun", dryRun);
	}

	private Map<String, Object> teachingReadiness(Map<String, Object> config) {
		String semester = text(config.get("semesterCode"));
		long taskCount = semester.isBlank() ? count("select count(*) from edu_course_offering")
				: count("select count(*) from edu_course_offering where semester_code=?", semester);
		long scheduleCount = semester.isBlank() ? count("select count(*) from edu_schedule_entry")
				: count("select count(*) from edu_schedule_entry where semester_code=? and status<>'CANCELLED'", semester);
		long missingMembers = semester.isBlank() ? 0 : count("""
				select count(*) from edu_course_offering offering
				where offering.semester_code=? and offering.status='ACTIVE'
				and not exists (select 1 from edu_teaching_class_member member where member.offering_id=offering.id and member.enrollment_status='ENROLLED')
				""", semester);
		return Map.of("semesterCode", semester, "courseOfferings", taskCount,
				"scheduleEntries", scheduleCount, "offeringsWithoutStudents", missingMembers);
	}

	private Map<String, Object> generateFullSchedule(Map<String, Object> config, String actor) {
		String semester = required(config.get("semesterCode"), "学期编码");
		var term = terms.findByTermCode(semester).orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		AutoScheduleCommand command = new AutoScheduleCommand(
				semester,
				text(config.getOrDefault("planName", "学期运营智能体全量排课")),
				"FULL",
				Set.of(),
				bounded(config.get("candidateCount"), 1, 5),
				bounded(config.get("weekdays"), 5, 7),
				bounded(config.get("periodsPerDay"), 8, 16),
				number(config.get("startWeek"), 1),
				number(config.get("endWeek"), term.getWeekCount()));
		var generated = autoScheduling.generate(command, actor);
		return Map.of("semesterCode", semester, "candidateCount", generated.size(),
				"candidates", generated.stream().map(value -> Map.of(
						"id", value.id(), "score", value.totalScore(),
						"unscheduledLessons", value.unscheduledLessons())).toList());
	}

	private Map<String, Object> submitBestSchedule(Map<String, Object> config, String actor) {
		String semester = required(config.get("semesterCode"), "学期编码");
		ScheduleCandidateView candidate = candidatesCreatedByCurrentRun(config, semester).stream()
				.filter(value -> "DRAFT".equals(value.reviewStatus()))
				.filter(value -> actor.equals(value.ownerUsername()))
				.filter(value -> value.unscheduledLessons() == 0)
				.max(Comparator.comparing(ScheduleCandidateView::totalScore))
				.orElseThrow(() -> new IllegalStateException("没有可提交且已完整排课的候选方案"));
		var submitted = autoScheduling.submitReview(candidate.id(), actor);
		return Map.of("semesterCode", semester, "candidateId", submitted.id(), "reviewStatus", submitted.reviewStatus());
	}

	private Map<String, Object> reviewSubmittedSchedule(Map<String, Object> config, String actor) {
		String semester = required(config.get("semesterCode"), "学期编码");
		ScheduleCandidateView candidate = candidatesCreatedByCurrentRun(config, semester).stream()
				.filter(value -> "SUBMITTED".equals(value.reviewStatus()))
				.findFirst().orElseThrow(() -> new IllegalStateException("没有待审核候选方案"));
		var reviewed = autoScheduling.review(candidate.id(), true, "学期运营智能体异人审核通过", actor);
		return Map.of("semesterCode", semester, "candidateId", reviewed.id(),
				"reviewStatus", reviewed.reviewStatus(), "reviewedBy", actor);
	}

	private Map<String, Object> applyApprovedSchedule(Map<String, Object> config, String actor) {
		String semester = required(config.get("semesterCode"), "学期编码");
		ScheduleCandidateView candidate = candidatesCreatedByCurrentRun(config, semester).stream()
				.filter(value -> "APPROVED".equals(value.reviewStatus()) && "CANDIDATE".equals(value.status()))
				.findFirst().orElseThrow(() -> new IllegalStateException("没有审核通过且尚未应用的候选方案"));
		var applied = autoScheduling.apply(candidate.id(), actor);
		return Map.of("semesterCode", semester, "candidateId", applied.id(), "status", applied.status());
	}

	/**
	 * A semester can retain many historical candidates. Restrict chained submit,
	 * review and apply operations to candidates generated after this run started.
	 */
	private List<ScheduleCandidateView> candidatesCreatedByCurrentRun(
			Map<String, Object> config,
			String semester) {
		Object startedAtValue = config.get("__runStartedAt");
		if (startedAtValue == null || startedAtValue.toString().isBlank()) {
			return autoScheduling.list(semester);
		}
		LocalDateTime startedAt = LocalDateTime.parse(startedAtValue.toString());
		return autoScheduling.list(semester).stream()
				.filter(value -> value.generatedAt() != null && !value.generatedAt().isBefore(startedAt))
				.toList();
	}

	private Map<String, Object> publishSchedule(Map<String, Object> config, String actor) {
		String semester = required(config.get("semesterCode"), "学期编码");
		var version = scheduleVersions.publish(semester, actor);
		return Map.of("semesterCode", semester, "versionId", version.getId(),
				"versionNo", version.getVersionNo(), "entryCount", version.getEntryCount());
	}

	private Map<String, Object> termEndCheck(Map<String, Object> config) {
		String semester = required(config.get("semesterCode"), "学期编码");
		long offeringCount = count("select count(*) from edu_course_offering where semester_code=? and status='ACTIVE'", semester);
		long gradebooks = count("select count(*) from edu_gradebook gradebook join edu_course_offering offering on offering.id=gradebook.offering_id where offering.semester_code=?", semester);
		long published = count("select count(*) from edu_gradebook gradebook join edu_course_offering offering on offering.id=gradebook.offering_id where offering.semester_code=? and gradebook.status='PUBLISHED'", semester);
		long pendingChanges = count("select count(*) from edu_student_status_change where status in ('PENDING','APPROVED_PENDING')");
		long deadOutbox = count("select count(*) from edu_domain_event_outbox where status='DEAD'");
		return Map.of("semesterCode", semester, "courseOfferings", offeringCount,
				"gradebookCount", gradebooks, "publishedGradebooks", published,
				"unpublishedGradebooks", Math.max(0, gradebooks - published),
				"pendingStudentChanges", pendingChanges, "deadDomainEvents", deadOutbox,
				"readyToClose", offeringCount > 0 && gradebooks > 0 && gradebooks == published
						&& pendingChanges == 0 && deadOutbox == 0);
	}

	/**
	 * 考试收口只读检查。考试计划、场次、考场和监考是独立业务表，不能用“有考试计划”
	 * 代替“考试已经具备执行条件”，因此把每一层数量和未完成数量都返回给候选结果。
	 */
	private Map<String, Object> termEndExamCheck(Map<String, Object> config) {
		String semester = required(config.get("semesterCode"), "学期编码");
		long plans = count("select count(*) from edu_exam_plan where semester_code=?", semester);
		long publishedPlans = count("select count(*) from edu_exam_plan where semester_code=? and status='PUBLISHED'", semester);
		long sessions = count("select count(*) from edu_exam_session session join edu_exam_plan plan on plan.id=session.plan_id where plan.semester_code=?", semester);
		long rooms = count("select count(*) from edu_exam_room room join edu_exam_session session on session.id=room.session_id join edu_exam_plan plan on plan.id=session.plan_id where plan.semester_code=?", semester);
		long assignedInvigilations = count("select count(*) from edu_exam_invigilation assignment join edu_exam_room room on room.id=assignment.room_id join edu_exam_session session on session.id=room.session_id join edu_exam_plan plan on plan.id=session.plan_id where plan.semester_code=? and assignment.status='ASSIGNED'", semester);
		long pendingChanges = count("select count(*) from edu_exam_invigilation_change change join edu_exam_invigilation assignment on assignment.id=change.assignment_id join edu_exam_room room on room.id=assignment.room_id join edu_exam_session session on session.id=room.session_id join edu_exam_plan plan on plan.id=session.plan_id where plan.semester_code=? and change.status='PENDING'", semester);
		return Map.of("semesterCode", semester, "examPlans", plans, "publishedExamPlans", publishedPlans,
				"examSessions", sessions, "examRooms", rooms, "assignedInvigilations", assignedInvigilations,
				"pendingInvigilationChanges", pendingChanges,
				"readyForExamClose", plans > 0 && plans == publishedPlans && sessions > 0 && rooms > 0
						&& assignedInvigilations > 0 && pendingChanges == 0);
	}

	/** 成绩收口检查单独暴露，便于 Agent 在发现未发布成绩时停在可解释的人工处理节点。 */
	private Map<String, Object> termEndGradeCheck(Map<String, Object> config) {
		String semester = required(config.get("semesterCode"), "学期编码");
		long total = count("select count(*) from edu_gradebook gradebook join edu_course_offering offering on offering.id=gradebook.offering_id where offering.semester_code=?", semester);
		long confirmed = count("select count(*) from edu_gradebook gradebook join edu_course_offering offering on offering.id=gradebook.offering_id where offering.semester_code=? and gradebook.status in ('CONFIRMED','PUBLISHED')", semester);
		long published = count("select count(*) from edu_gradebook gradebook join edu_course_offering offering on offering.id=gradebook.offering_id where offering.semester_code=? and gradebook.status='PUBLISHED'", semester);
		return Map.of("semesterCode", semester, "gradebooks", total, "confirmedGradebooks", confirmed,
				"publishedGradebooks", published, "unconfirmedGradebooks", Math.max(0, total - confirmed),
				"unpublishedGradebooks", Math.max(0, total - published),
				"readyForGradeClose", total > 0 && total == published);
	}

	/** 归档前检查不执行归档写入，避免 Agent 越权改变历史数据。 */
	private Map<String, Object> termEndArchiveCheck(Map<String, Object> config) {
		String semester = required(config.get("semesterCode"), "学期编码");
		long pendingStatusChanges = count("select count(*) from edu_student_status_change where status in ('PENDING','APPROVED_PENDING')");
		long deadEvents = count("select count(*) from edu_domain_event_outbox where status='DEAD'");
		long activeOfferings = count("select count(*) from edu_course_offering where semester_code=? and status='ACTIVE'", semester);
		return Map.of("semesterCode", semester, "activeOfferings", activeOfferings,
				"pendingStudentStatusChanges", pendingStatusChanges, "deadDomainEvents", deadEvents,
				"readyForArchive", activeOfferings > 0 && pendingStatusChanges == 0 && deadEvents == 0,
				"message", "归档写入由教务管理员在归档菜单确认后执行");
	}

	private Map<String, Object> prepareGraduation(Map<String, Object> config, String actor) {
		int admissionYear = number(config.get("graduatingAdmissionYear"), 0);
		if (admissionYear < 2000) throw new IllegalArgumentException("必须明确配置毕业年级入学年份");
		int limit = bounded(config.get("batchLimit"), 100, 500);
		LocalDate effectiveDate = date(config.get("effectiveDate"), LocalDate.now());
		var targets = students.findAllByOrderByStudentNo().stream()
				.filter(value -> Integer.valueOf(admissionYear).equals(value.getGradeYear()))
				.filter(value -> "ACTIVE".equals(value.getEnrollmentStatus()))
				.limit(limit).toList();
		int created = 0;
		int skipped = 0;
		for (var student : targets) {
			try {
				studentChanges.request(student.getId(), new StudentStatusChangeCommand(
						"GRADUATE", null, effectiveDate, "学期运营智能体生成毕业学籍异动申请"), actor);
				created++;
			} catch (IllegalStateException exception) {
				skipped++;
			}
		}
		return Map.of("graduatingAdmissionYear", admissionYear, "effectiveDate", effectiveDate,
				"targetCount", targets.size(), "createdRequests", created, "skipped", skipped);
	}

	private long count(String sql, Object... args) {
		Long value = jdbc.queryForObject(sql, Long.class, args);
		return value == null ? 0 : value;
	}

	private int bounded(Object value, int fallback, int maximum) {
		return Math.max(1, Math.min(number(value, fallback), maximum));
	}

	private int number(Object value, int fallback) {
		if (value instanceof Number number) return number.intValue();
		try { return Integer.parseInt(text(value)); } catch (NumberFormatException ignored) { return fallback; }
	}

	private boolean bool(Object value, boolean fallback) {
		if (value instanceof Boolean bool) return bool;
		return value == null ? fallback : Boolean.parseBoolean(text(value));
	}

	private LocalDate date(Object value, LocalDate fallback) {
		try { return value == null || text(value).isBlank() ? fallback : LocalDate.parse(text(value)); }
		catch (RuntimeException exception) { throw new IllegalArgumentException("日期必须使用 yyyy-MM-dd 格式"); }
	}

	private Set<String> stringSet(Object value) {
		if (!(value instanceof Iterable<?> iterable)) return Set.of();
		Set<String> result = new LinkedHashSet<>();
		for (Object item : iterable) if (!text(item).isBlank()) result.add(text(item));
		return result;
	}

	private String required(Object value, String label) {
		String result = text(value);
		if (result.isBlank()) throw new IllegalArgumentException(label + "不能为空");
		return result;
	}

	private String text(Object value) {
		return value == null ? "" : String.valueOf(value).trim();
	}

	private record TeachingAssignmentCandidate(
			TeacherAcademicProfile teacher,
			com.chronos.education.scheduling.model.Subject subject,
			com.chronos.education.scheduling.model.AdministrativeClass administrativeClass,
			int weeklyLessons) {}
}
