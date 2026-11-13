package com.chronos.education.operationagent.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.BellPeriod;
import com.chronos.education.scheduling.model.BellSchedule;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleCandidateView;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.chronos.education.scheduling.model.dto.AdmissionDtos.EnrollmentCommand;
import com.chronos.education.scheduling.model.dto.AdmissionDtos.ReviewCommand;
import com.chronos.education.scheduling.model.dto.StudentStatusChangeCommand;
import com.chronos.education.scheduling.service.AcademicDataService;
import com.chronos.education.scheduling.service.AcademicCalendarService;
import com.chronos.education.scheduling.service.AccommodationService;
import com.chronos.education.scheduling.service.AccommodationImportService;
import com.chronos.education.scheduling.service.AdmissionImportService;
import com.chronos.education.scheduling.service.AdmissionService;
import com.chronos.education.scheduling.service.AutoSchedulingService;
import com.chronos.education.scheduling.service.ClassSchedulingService;
import com.chronos.education.scheduling.service.OfficialHolidayImportService;
import com.chronos.education.scheduling.service.SchedulePlanVersionService;
import com.chronos.education.scheduling.service.SemesterPreparationExecutionService;
import com.chronos.education.scheduling.service.StudentAccountProvisioningService;
import com.chronos.education.scheduling.service.StudentStatusChangeService;
import com.chronos.file.service.ManagedFileService;
import com.chronos.knowledge.service.KnowledgeService;
import com.chronos.service.impl.DirectoryImportService;
import com.chronos.service.impl.EmployeeImportService;
import com.chronos.security.DirectoryDataScopeGuard;

/**
 * 学期运营智能体的受控工具边界。所有写入都复用现有领域服务，禁止模型生成 SQL 或绕开领域校验直接修改正式业务表。
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
	private final OperationAgentTeacherOnboardingTool teacherOnboarding;
	private final TeacherAcademicProfileRepository teachers;
	private final AcademicDataService academicData;
	private final AcademicCalendarService academicCalendar;
	private final OfficialHolidayImportService officialHolidays;
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
	private final DirectoryImportService directoryImports;
	private final OperationAgentExtendedBusinessTools extendedTools;
	private final DirectoryDataScopeGuard dataScopes;

	public OperationAgentBusinessTools(JdbcTemplate jdbc, AdmissionService admissions,
			AdmissionImportService admissionImports, ManagedFileService managedFiles, KnowledgeService knowledge,
			AdmissionCandidateRepository admissionCandidates, AdministrativeClassRepository administrativeClasses,
			StudentProfileRepository students, StudentAccountProvisioningService studentAccounts,
			EducationUserBindingRepository bindings, OperationAgentTeacherOnboardingTool teacherOnboarding,
			TeacherAcademicProfileRepository teachers, AcademicDataService academicData,
			AcademicCalendarService academicCalendar, OfficialHolidayImportService officialHolidays,
			TeacherTeachingAssignmentRepository teachingAssignments, CourseCatalogRepository courses,
			CourseOfferingRepository offerings, AcademicTermRepository terms, ClassSchedulingService scheduling,
			AutoSchedulingService autoScheduling, SchedulePlanVersionService scheduleVersions,
			StudentStatusChangeService studentChanges, SemesterPreparationExecutionService preparationExecutions,
			SubjectRepository subjects, AccommodationService accommodations,
			AccommodationImportService accommodationImports, DirectoryImportService directoryImports,
			OperationAgentExtendedBusinessTools extendedTools, DirectoryDataScopeGuard dataScopes) {
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
		this.teacherOnboarding = teacherOnboarding;
		this.teachers = teachers;
		this.academicData = academicData;
		this.academicCalendar = academicCalendar;
		this.officialHolidays = officialHolidays;
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
		this.directoryImports = directoryImports;
		this.extendedTools = extendedTools;
		this.dataScopes = dataScopes;
	}

	public Map<String, Object> execute(String toolCode, Map<String, Object> config, String knowledgeBaseId,
			String objective, String actor) {
		Map<String, Object> scopedConfig = config == null ? new LinkedHashMap<>() : new LinkedHashMap<>(config);
		if (requiresOrganizationScope(toolCode)) {
			scopedConfig.put("organizationId", resolveOrganization(scopedConfig, actor));
		}
		config = scopedConfig;
		return switch (toolCode) {
		case "academic.term.create" -> createAcademicTerm(config);
		case "academic.calendar.prepare" -> prepareAcademicCalendar(config);
		case "academic.bell.configure" -> configureBellSchedule(config, actor);
		case "semester.context.check" -> semesterContext(config, actor);
		case "knowledge.policy.search" -> knowledgePolicy(config, knowledgeBaseId, objective, actor);
		case "iam.department.import.managed-file" -> importDirectory(config, actor, "departments");
		case "iam.position.import.managed-file" -> importDirectory(config, actor, "positions");
		case "iam.job-level.import.managed-file" -> importDirectory(config, actor, "job-levels");
		case "iam.employee.import.managed-file" -> importDirectory(config, actor, "employees");
		case "admission.import.managed-file" -> importAdmissions(config, actor);
		case "admission.data.check" -> admissionCheck(config);
		case "admission.class.assign.batch" -> assignAdmissionClasses(config);
		case "admission.review.batch" -> reviewAdmissions(config, actor);
		case "admission.enroll.batch" -> enrollAdmissions(config);
		case "account.binding.check" -> accountBindingCheck(config);
		case "admission.accommodation.check" -> accommodationCheck(config);
		case "admission.accommodation.resource.check" -> accommodationResourceCheck(config);
		case "admission.accommodation.resource.maintain" -> accommodationResourceMaintain(config);
		case "admission.accommodation.plan" -> accommodationPlan(config);
		case "admission.accommodation.assign.batch" -> accommodationAssign(config, actor);
		case "student.account.provision.batch" -> provisionStudentAccounts(config);
		case OperationAgentTeacherOnboardingTool.CODE -> teacherOnboarding.execute(config, actor);
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
		default -> {
			if (!extendedTools.supports(toolCode)) {
				throw new IllegalArgumentException("未注册的业务工具：" + toolCode);
			}
			yield extendedTools.execute(toolCode, config, actor);
		}
		};
	}

	/**
	 * 学期创建必须复用教务领域服务，以保持机构编码、同机构唯一性和当前学期切换规则一致。 智能体检查点会先用 dryRun
	 * 生成候选证据，发起人确认后才写入正式数据。
	 */
	private Map<String, Object> createAcademicTerm(Map<String, Object> config) {
		String organizationId = required(config.get("organizationId"), "目标机构");
		String localTermCode = required(config.get("localTermCode"), "校内学期编码");
		int separator = localTermCode.indexOf(':');
		if (separator >= 0)
			localTermCode = localTermCode.substring(separator + 1).trim();
		String termName = required(config.get("termName"), "学期名称");
		String academicYear = required(config.get("academicYear"), "学年");
		int termNo = number(config.get("termNo"), 0);
		int weekCount = number(config.get("weekCount"), 0);
		if (termNo < 1 || termNo > 3)
			throw new IllegalArgumentException("学期序号必须在 1-3 之间");
		if (weekCount < 1 || weekCount > 60)
			throw new IllegalArgumentException("教学周数必须在 1-60 之间");
		LocalDate startDate = requiredDate(config.get("startDate"), "开始日期");
		LocalDate endDate = requiredDate(config.get("endDate"), "结束日期");
		if (!startDate.isBefore(endDate))
			throw new IllegalArgumentException("学期开始日期必须早于结束日期");

		String orgCode = jdbc.queryForObject("select org_code from t_organization where id=?", String.class,
				organizationId);
		String qualifiedCode = required(orgCode, "机构编码") + ":" + localTermCode;
		boolean exists = terms.existsByOrganizationIdAndLocalTermCode(organizationId, localTermCode);
		Map<String, Object> candidate = new LinkedHashMap<>();
		candidate.put("organizationId", organizationId);
		candidate.put("localTermCode", localTermCode);
		candidate.put("termCode", qualifiedCode);
		candidate.put("termName", termName);
		candidate.put("academicYear", academicYear);
		candidate.put("termNo", termNo);
		candidate.put("startDate", startDate.toString());
		candidate.put("endDate", endDate.toString());
		candidate.put("weekCount", weekCount);
		candidate.put("currentTerm", bool(config.get("currentTerm"), false));

		if (bool(config.get("dryRun"), false)) {
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("dryRun", true);
			result.put("valid", !exists);
			result.put("candidateCount", 1);
			// 后续候选步骤只把该值当作候选链路引用，不会访问正式学期表。
			result.put("termId", "candidate:" + qualifiedCode);
			result.put("rows", List.of(candidate));
			result.put("errors", exists ? List.of("该机构已存在校内学期编码 " + localTermCode) : List.of());
			result.put("message", exists ? "学期候选校验失败" : "学期候选数据已生成，等待发起人确认写入");
			return result;
		}

		AcademicTerm command = new AcademicTerm();
		command.setOrganizationId(organizationId);
		command.setLocalTermCode(localTermCode);
		command.setTermName(termName);
		command.setAcademicYear(academicYear);
		command.setTermNo(termNo);
		command.setStartDate(startDate);
		command.setEndDate(endDate);
		command.setWeekCount(weekCount);
		command.setCurrentTerm(bool(config.get("currentTerm"), false));
		AcademicTerm created = academicData.saveTerm(null, command);
		candidate.put("id", created.getId());
		candidate.put("termCode", created.getTermCode());
		return Map.of("dryRun", false, "created", true, "termId", created.getId(), "termCode", created.getTermCode(),
				"localTermCode", created.getLocalTermCode(), "message", "学年学期创建成功", "rows", List.of(candidate));
	}

	private Map<String, Object> prepareAcademicCalendar(Map<String, Object> config) {
		String termId = academicTermId(config);
		// 现有学期以业务记录为准。只有尚未写入的学期候选才使用计划里的日期。
		AcademicTerm term = termId.startsWith("candidate:") ? null : requireAcademicTerm(termId, config);
		LocalDate startDate = term == null ? requiredDate(config.get("startDate"), "开始日期") : term.getStartDate();
		LocalDate endDate = term == null ? requiredDate(config.get("endDate"), "结束日期") : term.getEndDate();
		if (startDate == null || endDate == null)
			throw new IllegalArgumentException("目标学期的起止日期未维护");
		if (startDate.isAfter(endDate))
			throw new IllegalArgumentException("教学日历开始日期不能晚于结束日期");
		List<Integer> years = java.util.stream.IntStream.rangeClosed(startDate.getYear(), endDate.getYear()).boxed()
				.toList();
		if (bool(config.get("dryRun"), false) || termId.startsWith("candidate:")) {
			return Map.of("dryRun", true, "valid", true, "termId", termId, "years", years, "message",
					"教学日历候选已生成；正式执行时将同步已公布且可核验的国务院节假日安排");
		}
		List<Map<String, Object>> imported = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		for (Integer year : years) {
			try {
				var result = officialHolidays.importOfficial(termId, year);
				imported.add(Map.of("year", result.year(), "sourceUrl", result.sourceUrl(), "counts", result.counts()));
			} catch (RuntimeException exception) {
				// 下一年度公告可能尚未发布。已公布年度仍然保留，未公布年度明确进入待维护提示。
				warnings.add(year + " 年节假日暂未同步：" + exception.getMessage());
			}
		}
		if (imported.isEmpty()) {
			throw new IllegalStateException("教学日历同步失败，所有目标年度均未导入。" + String.join("。", warnings));
		}
		return Map.of("dryRun", false, "termId", termId, "imported", imported, "warnings", warnings, "message",
				warnings.isEmpty() ? "教学日历已同步" : "部分年度已同步，未同步年度见警告");
	}

	private Map<String, Object> configureBellSchedule(Map<String, Object> config, String actor) {
		String termId = academicTermId(config);
		AcademicTerm term = termId.startsWith("candidate:") ? null : requireAcademicTerm(termId, config);
		String organizationId = required(config.get("organizationId"), "目标机构");
		String campusReference = required(config.get("campusId"), "适用校区");
		String scheduleName = required(config.get("scheduleName"), "作息方案名称");
		String campusId = resolveCampus(organizationId, campusReference, actor);
		List<Map<String, Object>> periods = periodCommands(config.get("periods"));
		String scheduleCode = text(config.get("scheduleCode"));
		if (scheduleCode.isBlank()) {
			String termCode = term == null ? termId.substring("candidate:".length()) : term.getTermCode();
			scheduleCode = "AGENT-"
					+ Integer.toUnsignedString((termCode + ':' + campusId + ':' + scheduleName).hashCode(), 36);
		}
		List<Map<String, Object>> rows = periods.stream()
				.map(value -> Map.<String, Object>of("periodNo", value.get("periodNo"), "periodName",
						value.get("periodName"), "daySegment", value.get("daySegment"), "startTime",
						value.get("startTime"), "endTime", value.get("endTime")))
				.toList();
		if (bool(config.get("dryRun"), false) || termId.startsWith("candidate:")) {
			return Map.of("dryRun", true, "valid", true, "termId", termId, "campusId", campusId, "scheduleCode",
					scheduleCode, "scheduleName", scheduleName, "rows", rows, "message",
					bool(config.get("generatedPeriods"), false) ? "已根据提供的规则和缺省设置计算节次，请核对各节起止时间后确认"
							: "校区作息候选已生成，等待发起人确认写入");
		}
		BellSchedule schedule = new BellSchedule();
		schedule.setAcademicTermId(termId);
		schedule.setCampusId(campusId);
		schedule.setScheduleCode(scheduleCode);
		schedule.setScheduleName(scheduleName);
		schedule.setDefaultSchedule(bool(config.get("defaultSchedule"), true));
		schedule.setStatus("ACTIVE");
		BellSchedule created = academicCalendar.saveBellSchedule(null, schedule);
		for (Map<String, Object> value : periods) {
			BellPeriod period = new BellPeriod();
			period.setBellScheduleId(created.getId());
			period.setPeriodNo(number(value.get("periodNo"), 0));
			period.setPeriodName(required(value.get("periodName"), "节次名称"));
			period.setDaySegment(required(value.get("daySegment"), "时段"));
			period.setStartTime(LocalTime.parse(required(value.get("startTime"), "开始时间")));
			period.setEndTime(LocalTime.parse(required(value.get("endTime"), "结束时间")));
			period.setSchedulable(bool(value.get("schedulable"), true));
			academicCalendar.saveBellPeriod(null, period);
		}
		return Map.of("dryRun", false, "created", true, "termId", termId, "campusId", campusId, "scheduleId",
				created.getId(), "scheduleCode", created.getScheduleCode(), "periodCount", periods.size(), "message",
				"校区作息方案创建成功");
	}

	private String academicTermId(Map<String, Object> config) {
		String termId = required(config.get("termId"), "目标学期");
		if (termId.startsWith("candidate:")) {
			if (!bool(config.get("dryRun"), false)) {
				throw new IllegalArgumentException("正式处理需要已创建的学期，请先完成学期创建步骤");
			}
		}
		return termId;
	}

	private AcademicTerm requireAcademicTerm(String termId, Map<String, Object> config) {
		String organizationId = required(config.get("organizationId"), "目标机构");
		var term = terms.findById(termId).orElseThrow(() -> new IllegalArgumentException("目标学期不存在"));
		if (!organizationId.equals(term.getOrganizationId())) {
			throw new IllegalArgumentException("目标学期不属于所选机构");
		}
		return term;
	}

	private List<Map<String, Object>> periodCommands(Object value) {
		if (!(value instanceof List<?> source) || source.isEmpty()) {
			throw new IllegalArgumentException("请提供至少一个节次时间");
		}
		List<Map<String, Object>> result = new ArrayList<>();
		Set<Integer> numbers = new LinkedHashSet<>();
		for (Object item : source) {
			if (!(item instanceof Map<?, ?> raw))
				throw new IllegalArgumentException("节次时间格式不正确");
			Map<String, Object> period = new LinkedHashMap<>();
			raw.forEach((key, field) -> period.put(String.valueOf(key), field));
			int periodNo = number(period.get("periodNo"), 0);
			if (periodNo < 1 || !numbers.add(periodNo))
				throw new IllegalArgumentException("节次序号必须大于 0 且不能重复");
			LocalTime start = LocalTime.parse(required(period.get("startTime"), "开始时间"));
			LocalTime end = LocalTime.parse(required(period.get("endTime"), "结束时间"));
			if (!start.isBefore(end))
				throw new IllegalArgumentException("节次开始时间必须早于结束时间");
			period.put("periodNo", periodNo);
			period.put("periodName", required(period.get("periodName"), "节次名称"));
			period.put("daySegment", required(period.get("daySegment"), "时段"));
			period.put("startTime", start.toString());
			period.put("endTime", end.toString());
			result.add(period);
		}
		return result.stream().sorted(Comparator.comparingInt(item -> number(item.get("periodNo"), 0))).toList();
	}

	private String resolveCampus(String organizationId, String reference, String actor) {
		List<String> ids = jdbc.queryForList(
				"select id from t_organization where status=1 and (id=? or org_code=? or organization_name=?)",
				String.class, reference, reference, reference);
		if (ids.isEmpty())
			throw new IllegalArgumentException("适用校区不存在或已停用：" + reference);
		if (ids.size() > 1)
			throw new IllegalArgumentException("适用校区不唯一，请使用校区编码或 ID");
		String campusId = ids.getFirst();
		Long belongs = jdbc.queryForObject("""
				with recursive tree as (
				  select id from t_organization where id=?
				  union
				  select child.id from t_organization child join tree parent on child.parent_org_id=parent.id
				)
				select count(*) from tree where id=?
				""", Long.class, organizationId, campusId);
		if (belongs == null || belongs == 0)
			throw new IllegalArgumentException("适用校区不属于目标机构");
		dataScopes.requireOrganization(authentication(actor), campusId);
		return campusId;
	}

	private Map<String, Object> importDirectory(Map<String, Object> config, String actor, String type) {
		String fileId = required(config.get("managedFileId"), "业务文件 ID");
		String organizationRef = required(config.get("organizationId"), "目标机构");
		List<String> organizationIds = jdbc.queryForList(
				"select id from t_organization where status=1 and (id=? or org_code=? or organization_name=?)",
				String.class, organizationRef, organizationRef, organizationRef);
		if (organizationIds.isEmpty())
			throw new IllegalArgumentException("目标机构不存在或已停用：" + organizationRef);
		if (organizationIds.size() > 1)
			throw new IllegalArgumentException("目标机构不唯一，请使用机构编码或 ID");
		String organizationId = organizationIds.getFirst();
		dataScopes.requireOrganization(authentication(actor), organizationId);
		boolean dryRun = bool(config.get("dryRun"), true);
		boolean syncAccounts = "employees".equals(type) && bool(config.get("syncAccounts"), true);
		boolean disableInactive = "employees".equals(type) && bool(config.get("disableInactiveAccounts"), false);
		var authentication = authentication(actor);
		var file = managedFiles.read(fileId, authentication);
		if (!file.filename().toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
			throw new IllegalArgumentException("目录业务文件必须是 xlsx 文件");
		}
		Map<String, Object> result = new LinkedHashMap<>(
				directoryImports.importBytes(type, file.content(), dryRun, organizationId,
						new EmployeeImportService.ImportOptions(syncAccounts, disableInactive, "AGENT", dryRun)));
		result.put("managedFileId", fileId);
		result.put("filename", file.filename());
		result.put("syncAccounts", syncAccounts);
		return result;
	}

	private Map<String, Object> semesterContext(Map<String, Object> config, String actor) {
		String semesterCode = text(config.get("semesterCode"));
		String organizationId = resolveOrganization(config, actor);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("semesterCode", semesterCode);
		result.put("organizationId", organizationId);
		result.put("courseOfferingCount",
				count(semesterCode.isBlank() ? "select count(*) from edu_course_offering where campus_id=?"
						: "select count(*) from edu_course_offering where semester_code = ? and campus_id = ?",
						semesterCode.isBlank() ? new Object[] { organizationId }
								: new Object[] { semesterCode, organizationId }));
		result.put("activeClassCount", count(
				"select count(*) from edu_administrative_class where status='ACTIVE' and campus_id=?", organizationId));
		result.put("activeStudentCount", count(
				"select count(*) from edu_student_profile student join edu_administrative_class class on class.id=student.administrative_class_id where student.enrollment_status='ACTIVE' and class.campus_id=?",
				organizationId));
		result.put("activeTeacherCount", count(
				"select count(distinct teacher.id) from edu_teacher_profile teacher join t_employee_assignment assignment on assignment.employee_id=teacher.employee_id where teacher.enabled=true and teacher.employment_status='ACTIVE' and assignment.organization_id=? and assignment.status=1",
				organizationId));
		return result;
	}

	private Map<String, Object> knowledgePolicy(Map<String, Object> config, String knowledgeBaseId, String objective,
			String actor) {
		if (knowledgeBaseId == null || knowledgeBaseId.isBlank()) {
			return Map.of("referenceCount", 0, "message", "计划未绑定知识库，本步骤未引用校内政策");
		}
		String query = text(config.getOrDefault("query", objective));
		var authentication = new UsernamePasswordAuthenticationToken(actor, "", List.of());
		var answer = knowledge.ask(knowledgeBaseId, query, authentication);
		return Map.of("referenceCount", answer.references().size(), "query", query, "answer", answer.answer(),
				"modelGenerated", answer.modelGenerated(), "references", answer.references());
	}

	private Map<String, Object> importAdmissions(Map<String, Object> config, String actor) {
		String fileId = required(config.get("managedFileId"), "录取名册文件 ID");
		boolean dryRun = bool(config.get("dryRun"), true);
		String organizationId = resolveOrganization(config, actor);
		var authentication = authentication(actor);
		var file = managedFiles.read(fileId, authentication);
		if (!file.filename().toLowerCase().endsWith(".xlsx")) {
			throw new IllegalArgumentException("录取名册必须是 xlsx 文件");
		}
		var result = admissionImports.importBytes(file.content(), dryRun, organizationId);
		if (!result.errors().isEmpty()) {
			throw new IllegalArgumentException(
					"新生名册校验失败（" + result.errors().size() + " 行）：" + String.join("；", result.errors()));
		}
		return Map.of("managedFileId", fileId, "filename", file.filename(), "dryRun", dryRun, "totalRows",
				result.totalCount(), "importedRows", result.successCount(), "errors", result.errors(), "rows",
				result.rows(), "candidateNos", result.rows().stream().map(value -> value.candidateNo()).toList());
	}

	private Map<String, Object> admissionCheck(Map<String, Object> config) {
		int year = number(config.get("admissionYear"), Year.now().getValue());
		String organizationId = required(config.get("organizationId"), "目标机构");
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("admissionYear", year);
		result.put("organizationId", organizationId);
		result.put("total",
				count("select count(*) from edu_admission_candidate where admission_year=? and organization_id=?", year,
						organizationId));
		result.put("admitted", count(
				"select count(*) from edu_admission_candidate where admission_year=? and organization_id=? and admission_status='ADMITTED'",
				year, organizationId));
		result.put("verified", count(
				"select count(*) from edu_admission_candidate where admission_year=? and organization_id=? and admission_status='VERIFIED'",
				year, organizationId));
		result.put("enrolled", count(
				"select count(*) from edu_admission_candidate where admission_year=? and organization_id=? and admission_status='ENROLLED'",
				year, organizationId));
		result.put("missingMajor", count(
				"select count(*) from edu_admission_candidate where admission_year=? and organization_id=? and admitted_major_id is null",
				year, organizationId));
		result.put("missingPlannedClass", count(
				"select count(*) from edu_admission_candidate where admission_year=? and organization_id=? and planned_class_id is null",
				year, organizationId));
		return result;
	}

	/** 只读返回住宿容量和未分配清单，正式分配仍由住宿管理人工确认。 */
	private Map<String, Object> accommodationCheck(Map<String, Object> config) {
		String semester = required(config.get("semesterCode"), "学期编码");
		int year = number(config.get("admissionYear"), Year.now().getValue());
		String organizationId = required(config.get("organizationId"), "目标机构");
		long students = count(
				"select count(*) from edu_student_profile student join edu_administrative_class class on class.id=student.administrative_class_id where student.enrollment_status='ACTIVE' and student.grade_year=? and class.campus_id=?",
				year, organizationId);
		long assigned = count(
				"select count(*) from edu_student_accommodation assignment join edu_student_profile student on student.id=assignment.student_id join edu_administrative_class class on class.id=student.administrative_class_id where assignment.semester_code=? and assignment.status='ACTIVE' and class.campus_id=?",
				semester, organizationId);
		long beds = count(
				"select count(*) from edu_dorm_bed bed join edu_dorm_room room on room.id=bed.room_id join edu_dorm_building building on building.id=room.building_id where bed.status='AVAILABLE' and building.organization_id=?",
				organizationId);
		List<Map<String, Object>> unassigned = jdbc.queryForList(
				"""
						select student.id, student.student_no, student.student_name
						from edu_student_profile student
						join edu_administrative_class class on class.id=student.administrative_class_id
						where student.enrollment_status='ACTIVE' and student.grade_year=?
						  and class.campus_id=?
						  and not exists (select 1 from edu_student_accommodation assignment
						                  where assignment.student_id=student.id and assignment.semester_code=? and assignment.status='ACTIVE')
						order by student.student_no limit 100
						""",
				year, organizationId, semester);
		return Map.of("semesterCode", semester, "admissionYear", year, "activeStudents", students, "assignedStudents",
				assigned, "unassignedStudents", Math.max(0, students - assigned), "availableBeds", beds,
				"capacityEnough", beds >= Math.max(0, students - assigned), "manualEntryRoute",
				"/admin/education/accommodations", "unassignedPreview", unassigned);
	}

	private Map<String, Object> accommodationResourceCheck(Map<String, Object> config) {
		String organizationId = required(config.get("organizationId"), "目标机构");
		List<Map<String, Object>> rooms = jdbc.queryForList("""
				select building.building_code as "buildingCode", building.building_name as "buildingName",
				       room.room_no as "roomNo", room.floor_no as "floorNo", room.capacity,
				       count(bed.id) as "bedCount", room.capacity - count(bed.id) as "missingBedCount"
				from edu_dorm_room room
				join edu_dorm_building building on building.id = room.building_id
				left join edu_dorm_bed bed on bed.room_id = room.id
				where building.organization_id=?
				group by building.building_code, building.building_name, room.room_no, room.floor_no, room.capacity
				having count(bed.id) <> room.capacity
				order by building.building_code, room.floor_no, room.room_no
				""", organizationId);
		return Map.of("valid", rooms.isEmpty(), "issueCount", rooms.size(), "issues", rooms, "manualEntryRoute",
				"/admin/education/accommodations", "message",
				rooms.isEmpty() ? "宿舍房间容量与床位数据一致" : "发现房间容量与床位数量不一致，可执行资源维护补齐床位");
	}

	private Map<String, Object> accommodationResourceMaintain(Map<String, Object> config) {
		Object value = config.get("resources");
		if (!(value instanceof List<?> values) || values.isEmpty())
			throw new IllegalArgumentException("宿舍资源维护必须提供 resources 候选");
		List<Map<?, ?>> rows = new java.util.ArrayList<>();
		for (Object item : values) {
			if (!(item instanceof Map<?, ?> row))
				throw new IllegalArgumentException("宿舍资源候选格式不正确");
			rows.add(row);
		}
		int count = accommodationImports.maintainRows(rows, bool(config.get("dryRun"), false),
				required(config.get("organizationId"), "目标机构"));
		return Map.of("dryRun", bool(config.get("dryRun"), false), "processedCount", count, "requiresConfirmation",
				true, "manualEntryRoute", "/admin/education/accommodations");
	}

	private Map<String, Object> accommodationPlan(Map<String, Object> config) {
		String semester = required(config.get("semesterCode"), "学期编码");
		int year = number(config.get("admissionYear"), Year.now().getValue());
		int limit = bounded(config.get("batchLimit"), 100, 500);
		String organizationId = required(config.get("organizationId"), "目标机构");
		List<Map<String, Object>> students = jdbc.queryForList(
				"""
						select student.id student_id, student.student_no, student.student_name
						from edu_student_profile student
						join edu_administrative_class class on class.id=student.administrative_class_id
						where student.enrollment_status='ACTIVE' and student.grade_year=?
						  and class.campus_id=?
						  and not exists (select 1 from edu_student_accommodation assignment
						                  where assignment.student_id=student.id and assignment.semester_code=? and assignment.status='ACTIVE')
						order by student.student_no limit ?
						""",
				year, organizationId, semester, limit);
		List<Map<String, Object>> beds = jdbc.queryForList(
				"""
						select bed.id bed_id, building.building_name, room.room_no, bed.bed_no
						from edu_dorm_bed bed
						join edu_dorm_room room on room.id=bed.room_id
						join edu_dorm_building building on building.id=room.building_id
						where bed.status='AVAILABLE'
						  and building.organization_id=?
						  and not exists (select 1 from edu_student_accommodation assignment
						                  where assignment.bed_id=bed.id and assignment.semester_code=? and assignment.status='ACTIVE')
						order by building.building_code, room.floor_no, room.room_no, bed.bed_no limit ?
						""",
				organizationId, semester, limit);
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
				"assignments", assignments, "requiresConfirmation", true, "manualEntryRoute",
				"/admin/education/accommodations");
	}

	private Map<String, Object> accommodationAssign(Map<String, Object> config, String actor) {
		String semester = required(config.get("semesterCode"), "学期编码");
		Object value = config.get("assignments");
		if (!(value instanceof List<?> values) || values.isEmpty())
			throw new IllegalArgumentException("住宿分配必须提供已确认的 assignments 候选");
		if (bool(config.get("dryRun"), false))
			return Map.of("semesterCode", semester, "dryRun", true, "candidateCount", values.size(), "message",
					"候选模式未写入正式住宿关系");
		int assigned = 0;
		String organizationId = required(config.get("organizationId"), "目标机构");
		for (Object item : values) {
			if (!(item instanceof Map<?, ?> pair))
				throw new IllegalArgumentException("住宿分配候选格式不正确");
			String studentId = text(pair.get("studentId"));
			String bedId = text(pair.get("bedId"));
			if (count(
					"select count(*) from edu_student_profile student join edu_administrative_class class on class.id=student.administrative_class_id where student.id=? and class.campus_id=?",
					studentId, organizationId) != 1
					|| count(
							"select count(*) from edu_dorm_bed bed join edu_dorm_room room on room.id=bed.room_id join edu_dorm_building building on building.id=room.building_id where bed.id=? and building.organization_id=?",
							bedId, organizationId) != 1) {
				throw new IllegalArgumentException("住宿分配候选包含其他机构的学生或床位");
			}
			accommodations.assign(new AccommodationService.AssignmentCommand(semester, studentId, bedId), actor);
			assigned++;
		}
		return Map.of("semesterCode", semester, "assignedCount", assigned, "candidateCount", values.size());
	}

	private Map<String, Object> assignAdmissionClasses(Map<String, Object> config) {
		var preview = attachedAdmissionPreview(config, "拟分班");
		if (preview != null)
			return preview;
		int year = number(config.get("admissionYear"), Year.now().getValue());
		int limit = bounded(config.get("batchLimit"), 100, 500);
		int targetSize = bounded(config.get("targetClassSize"), 50, 200);
		return preparationExecutions.assignClasses(year, limit, targetSize, bool(config.get("dryRun"), false),
				required(config.get("organizationId"), "目标机构"), admissionCandidateScope(config));
	}

	private Map<String, Object> reviewAdmissions(Map<String, Object> config, String actor) {
		var preview = attachedAdmissionPreview(config, "资格复查");
		if (preview != null)
			return preview;
		int year = number(config.get("admissionYear"), Year.now().getValue());
		int limit = bounded(config.get("batchLimit"), 100, 500);
		return preparationExecutions.reviewAdmissions(year, limit, bool(config.get("dryRun"), false), actor,
				required(config.get("organizationId"), "目标机构"), admissionCandidateScope(config));
	}

	private Map<String, Object> enrollAdmissions(Map<String, Object> config) {
		var preview = attachedAdmissionPreview(config, "报到建档与账号开通");
		if (preview != null)
			return preview;
		int year = number(config.get("admissionYear"), Year.now().getValue());
		int limit = bounded(config.get("batchLimit"), 50, 200);
		return preparationExecutions.enrollAdmissions(year, limit, bool(config.get("dryRun"), false),
				required(config.get("organizationId"), "目标机构"), admissionCandidateScope(config));
	}

	/** 空集合表示只处理零人，绝不能退化成整年级操作；引用缺失则拒绝执行。 */
	private List<String> admissionCandidateScope(Map<String, Object> config) {
		if (!config.containsKey("candidateNos"))
			return null;
		if (!(config.get("candidateNos") instanceof List<?> values)) {
			throw new IllegalArgumentException("新生处理范围缺失，请先完成名册校验");
		}
		return values.stream().map(String::valueOf).toList();
	}

	/** 未正式导入的名册也应能核对后续对象，不能用当前库的零条记录伪装成候选完成。 */
	private Map<String, Object> attachedAdmissionPreview(Map<String, Object> config, String stage) {
		if (!bool(config.get("dryRun"), false) || !config.containsKey("importRows")
				|| !Boolean.TRUE.equals(config.get("importWasDryRun")))
			return null;
		admissionCandidateScope(config);
		if (!(config.get("importRows") instanceof List<?> rows)) {
			throw new IllegalArgumentException("新生名册候选证据缺失");
		}
		long eligible = rows.stream().filter(value -> value instanceof Map<?, ?> row
				&& row.get("admittedMajorId") != null && row.get("plannedClassId") != null).count();
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("dryRun", true);
		result.put("stage", stage);
		result.put("candidateCount", rows.size());
		result.put("eligible", eligible);
		result.put("succeeded", 0);
		result.put("valid", eligible == rows.size());
		result.put("rows", rows);
		result.put("message", eligible == rows.size() ? "基于已校验名册生成候选；正式导入及前置步骤完成后，将重新核验并仅处理这些考生。尚未创建学籍或账号。"
				: "部分名册尚缺专业或拟分班，请先补全分班候选；未写入正式数据。");
		return result;
	}

	private Map<String, Object> accountBindingCheck(Map<String, Object> config) {
		String organizationId = required(config.get("organizationId"), "目标机构");
		long activeStudents = count(
				"select count(*) from edu_student_profile student join edu_administrative_class class on class.id=student.administrative_class_id where student.enrollment_status='ACTIVE' and class.campus_id=?",
				organizationId);
		long studentBindings = count(
				"select count(distinct binding.profile_id) from edu_user_profile_binding binding join edu_student_profile student on student.id=binding.profile_id join edu_administrative_class class on class.id=student.administrative_class_id where binding.profile_type='STUDENT' and binding.status='ACTIVE' and class.campus_id=?",
				organizationId);
		long activeTeachers = count(
				"select count(distinct teacher.id) from edu_teacher_profile teacher join t_employee_assignment assignment on assignment.employee_id=teacher.employee_id where teacher.enabled=true and teacher.employment_status='ACTIVE' and assignment.status=1 and assignment.organization_id=?",
				organizationId);
		long teacherBindings = count(
				"select count(distinct binding.profile_id) from edu_user_profile_binding binding join edu_teacher_profile teacher on teacher.id=binding.profile_id join t_employee_assignment assignment on assignment.employee_id=teacher.employee_id where binding.profile_type='TEACHER' and binding.status='ACTIVE' and assignment.status=1 and assignment.organization_id=?",
				organizationId);
		return Map.of("activeStudents", activeStudents, "boundStudentAccounts", studentBindings,
				"missingStudentBindings", Math.max(0, activeStudents - studentBindings), "activeTeachers",
				activeTeachers, "boundTeacherAccounts", teacherBindings, "missingTeacherBindings",
				Math.max(0, activeTeachers - teacherBindings));
	}

	private Map<String, Object> provisionStudentAccounts(Map<String, Object> config) {
		var preview = attachedAdmissionPreview(config, "学生账号开通");
		if (preview != null)
			return preview;
		List<String> candidateNos = admissionCandidateScope(config);
		int limit = bounded(config.get("batchLimit"), 100, 500);
		boolean dryRun = bool(config.get("dryRun"), false);
		String organizationId = required(config.get("organizationId"), "目标机构");
		var missing = students.findAllByOrderByStudentNo().stream()
				.filter(value -> candidateNos == null || candidateNos.contains(value.getCandidateNo()))
				.filter(value -> "ACTIVE".equals(value.getEnrollmentStatus()))
				.filter(value -> bindings.findByProfileTypeAndProfileIdAndStatus("STUDENT", value.getId(), "ACTIVE")
						.isEmpty())
				.filter(value -> administrativeClasses.findById(value.getAdministrativeClassId())
						.map(item -> organizationId.equals(item.getCampusId())).orElse(false))
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

	private Map<String, Object> generateTeachingOfferings(Map<String, Object> config) {
		String semesterCode = required(config.get("semesterCode"), "学期编码");
		int limit = bounded(config.get("batchLimit"), 100, 500);
		return preparationExecutions.generateOfferings(semesterCode, limit, bool(config.get("dryRun"), false),
				required(config.get("organizationId"), "目标机构"));
	}

	/**
	 * 根据用户确认的工号、学科编码和班级编码批量建立教师分工。 全批次先解析验证再写入，避免配置中途出错造成半批数据。
	 */
	private Map<String, Object> generateTeachingAssignments(Map<String, Object> config) {
		String semesterCode = required(config.get("semesterCode"), "学期编码");
		String organizationId = required(config.get("organizationId"), "目标机构");
		boolean dryRun = bool(config.get("dryRun"), false);
		var term = terms.findByTermCode(semesterCode).orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		if (!organizationId.equals(term.getOrganizationId()))
			throw new IllegalArgumentException("学期不属于当前智能体任务的目标机构");
		Object raw = config.get("assignments");
		if (!(raw instanceof Iterable<?> iterable)) {
			throw new IllegalArgumentException("教师分工必须提供 assignments 数组");
		}
		List<TeachingAssignmentCandidate> candidates = new ArrayList<>();
		for (Object item : iterable) {
			if (!(item instanceof Map<?, ?> source))
				throw new IllegalArgumentException("教师分工项必须是对象");
			String teacherNo = required(source.get("teacherNo"), "教师工号");
			String subjectCode = required(source.get("subjectCode"), "学科编码");
			String classCode = required(source.get("classCode"), "行政班编码");
			var teacher = teachers.findByTeacherNo(teacherNo).filter(
					value -> Boolean.TRUE.equals(value.getEnabled()) && "ACTIVE".equals(value.getEmploymentStatus()))
					.orElseThrow(() -> new IllegalArgumentException("教师不存在或未启用：" + teacherNo));
			if (count(
					"select count(*) from t_employee_assignment where employee_id=? and organization_id=? and status=1",
					teacher.getEmployeeId(), organizationId) == 0)
				throw new IllegalArgumentException("教师不属于目标机构：" + teacherNo);
			var subject = subjects.findBySubjectCode(subjectCode)
					.filter(value -> Boolean.TRUE.equals(value.getEnabled()))
					.orElseThrow(() -> new IllegalArgumentException("学科不存在或未启用：" + subjectCode));
			var administrativeClass = administrativeClasses.findByClassCode(classCode)
					.filter(value -> "ACTIVE".equals(value.getStatus()))
					.orElseThrow(() -> new IllegalArgumentException("行政班不存在或未启用：" + classCode));
			if (!organizationId.equals(administrativeClass.getCampusId()))
				throw new IllegalArgumentException("行政班不属于目标机构：" + classCode);
			int weeklyLessons = Math.max(1, number(source.get("weeklyLessons"), 2));
			candidates.add(new TeachingAssignmentCandidate(teacher, subject, administrativeClass, weeklyLessons));
		}
		if (candidates.isEmpty())
			throw new IllegalArgumentException("教师分工数组不能为空");
		var existing = teachingAssignments.findAllByOrderByCreateTimeDesc().stream()
				.filter(value -> term.getId().equals(value.getAcademicTermId())).toList();
		int created = 0;
		int skipped = 0;
		for (TeachingAssignmentCandidate candidate : candidates) {
			boolean duplicate = existing.stream()
					.anyMatch(value -> candidate.teacher().getId().equals(value.getTeacherId())
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
		return Map.of("semesterCode", semesterCode, "candidateCount", candidates.size(), "created", created,
				"skippedExisting", skipped, "dryRun", dryRun);
	}

	private Map<String, Object> teachingReadiness(Map<String, Object> config) {
		String semester = text(config.get("semesterCode"));
		String organizationId = required(config.get("organizationId"), "目标机构");
		long taskCount = semester.isBlank()
				? count("select count(*) from edu_course_offering where campus_id=?", organizationId)
				: count("select count(*) from edu_course_offering where semester_code=? and campus_id=?", semester,
						organizationId);
		long scheduleCount = semester.isBlank() ? count(
				"select count(*) from edu_schedule_entry entry join edu_course_offering offering on offering.id=entry.offering_id where offering.campus_id=?",
				organizationId)
				: count("select count(*) from edu_schedule_entry entry join edu_course_offering offering on offering.id=entry.offering_id where entry.semester_code=? and entry.status<>'CANCELLED' and offering.campus_id=?",
						semester, organizationId);
		long missingMembers = semester.isBlank() ? 0
				: count("""
						select count(*) from edu_course_offering offering
						where offering.semester_code=? and offering.status='ACTIVE' and offering.campus_id=?
						and not exists (select 1 from edu_teaching_class_member member where member.offering_id=offering.id and member.enrollment_status='ENROLLED')
						""",
						semester, organizationId);
		return Map.of("semesterCode", semester, "courseOfferings", taskCount, "scheduleEntries", scheduleCount,
				"offeringsWithoutStudents", missingMembers);
	}

	private Map<String, Object> generateFullSchedule(Map<String, Object> config, String actor) {
		String semester = required(config.get("semesterCode"), "学期编码");
		String organizationId = required(config.get("organizationId"), "目标机构");
		var term = terms.findByTermCode(semester).orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		if (!organizationId.equals(term.getOrganizationId()))
			throw new IllegalArgumentException("学期不属于当前智能体任务的目标机构");
		AutoScheduleCommand command = new AutoScheduleCommand(semester,
				text(config.getOrDefault("planName", "学期运营智能体全量排课")), "FULL", Set.of(),
				bounded(config.get("candidateCount"), 1, 5), bounded(config.get("weekdays"), 5, 7),
				bounded(config.get("periodsPerDay"), 8, 16), number(config.get("startWeek"), 1),
				number(config.get("endWeek"), term.getWeekCount()));
		var generated = autoScheduling.generate(command, actor);
		return Map.of("semesterCode", semester, "candidateCount", generated.size(), "candidates",
				generated.stream().map(value -> Map.of("id", value.id(), "score", value.totalScore(),
						"unscheduledLessons", value.unscheduledLessons())).toList());
	}

	private Map<String, Object> submitBestSchedule(Map<String, Object> config, String actor) {
		String semester = required(config.get("semesterCode"), "学期编码");
		requireTermOrganization(semester, required(config.get("organizationId"), "目标机构"));
		ScheduleCandidateView candidate = candidatesCreatedByCurrentRun(config, semester).stream()
				.filter(value -> "DRAFT".equals(value.reviewStatus()))
				.filter(value -> actor.equals(value.ownerUsername())).filter(value -> value.unscheduledLessons() == 0)
				.max(Comparator.comparing(ScheduleCandidateView::totalScore))
				.orElseThrow(() -> new IllegalStateException("没有可提交且已完整排课的候选方案"));
		if (bool(config.get("dryRun"), false))
			return scheduleCheckpointPreview(semester, candidate, "待确认提交审核");
		var submitted = autoScheduling.submitReview(candidate.id(), actor);
		return Map.of("semesterCode", semester, "candidateId", submitted.id(), "reviewStatus",
				submitted.reviewStatus());
	}

	private Map<String, Object> reviewSubmittedSchedule(Map<String, Object> config, String actor) {
		String semester = required(config.get("semesterCode"), "学期编码");
		requireTermOrganization(semester, required(config.get("organizationId"), "目标机构"));
		ScheduleCandidateView candidate = candidatesCreatedByCurrentRun(config, semester).stream()
				.filter(value -> "SUBMITTED".equals(value.reviewStatus())).findFirst()
				.orElseThrow(() -> new IllegalStateException("没有待审核候选方案"));
		if (bool(config.get("dryRun"), false))
			return scheduleCheckpointPreview(semester, candidate, "待独立审核");
		var reviewed = autoScheduling.review(candidate.id(), true, "学期运营智能体异人审核通过", actor);
		return Map.of("semesterCode", semester, "candidateId", reviewed.id(), "reviewStatus", reviewed.reviewStatus(),
				"reviewedBy", actor);
	}

	private Map<String, Object> applyApprovedSchedule(Map<String, Object> config, String actor) {
		String semester = required(config.get("semesterCode"), "学期编码");
		requireTermOrganization(semester, required(config.get("organizationId"), "目标机构"));
		ScheduleCandidateView candidate = candidatesCreatedByCurrentRun(config, semester).stream()
				.filter(value -> "APPROVED".equals(value.reviewStatus()) && "CANDIDATE".equals(value.status()))
				.findFirst().orElseThrow(() -> new IllegalStateException("没有审核通过且尚未应用的候选方案"));
		if (bool(config.get("dryRun"), false))
			return scheduleCheckpointPreview(semester, candidate, "待确认应用到课表草稿");
		var applied = autoScheduling.apply(candidate.id(), actor);
		return Map.of("semesterCode", semester, "candidateId", applied.id(), "status", applied.status());
	}

	/**
	 * A semester can retain many historical candidates. Restrict chained submit,
	 * review and apply operations to candidates generated after this run started.
	 */
	private List<ScheduleCandidateView> candidatesCreatedByCurrentRun(Map<String, Object> config, String semester) {
		if (config.containsKey("generatedCandidates")) {
			if (!(config.get("generatedCandidates") instanceof List<?> generated)) {
				throw new IllegalArgumentException("缺少本次排课生成的候选证据");
			}
			Set<String> ids = new LinkedHashSet<>();
			for (Object item : generated) {
				if (item instanceof Map<?, ?> row && row.get("id") instanceof String id)
					ids.add(id);
			}
			return autoScheduling.list(semester).stream().filter(value -> ids.contains(value.id())).toList();
		}
		String candidateId = text(config.get("candidateId"));
		if (config.containsKey("candidateId") && candidateId.isBlank()) {
			throw new IllegalArgumentException("缺少本次排课的目标候选编号");
		}
		if (!candidateId.isBlank()) {
			return autoScheduling.list(semester).stream().filter(value -> candidateId.equals(value.id())).toList();
		}
		Object startedAtValue = config.get("__runStartedAt");
		if (startedAtValue == null || startedAtValue.toString().isBlank()) {
			return autoScheduling.list(semester);
		}
		LocalDateTime startedAt = LocalDateTime.parse(startedAtValue.toString());
		return autoScheduling.list(semester).stream()
				.filter(value -> value.generatedAt() != null && !value.generatedAt().isBefore(startedAt)).toList();
	}

	private Map<String, Object> scheduleCheckpointPreview(String semester, ScheduleCandidateView candidate,
			String message) {
		return Map.of("semesterCode", semester, "candidateId", candidate.id(), "reviewStatus", candidate.reviewStatus(),
				"status", candidate.status(), "score", candidate.totalScore(), "unscheduledLessons",
				candidate.unscheduledLessons(), "dryRun", true, "message", message);
	}

	private Map<String, Object> publishSchedule(Map<String, Object> config, String actor) {
		String semester = required(config.get("semesterCode"), "学期编码");
		requireTermOrganization(semester, required(config.get("organizationId"), "目标机构"));
		if (bool(config.get("dryRun"), false)) {
			return Map.of("semesterCode", semester, "dryRun", true, "message", "待确认发布当前课表草稿，尚未发布");
		}
		var version = scheduleVersions.publish(semester, actor);
		return Map.of("semesterCode", semester, "versionId", version.getId(), "versionNo", version.getVersionNo(),
				"entryCount", version.getEntryCount());
	}

	private Map<String, Object> termEndCheck(Map<String, Object> config) {
		String semester = required(config.get("semesterCode"), "学期编码");
		String organizationId = required(config.get("organizationId"), "目标机构");
		requireTermOrganization(semester, organizationId);
		long offeringCount = count(
				"select count(*) from edu_course_offering where semester_code=? and status='ACTIVE' and campus_id=?",
				semester, organizationId);
		long gradebooks = count(
				"select count(*) from edu_gradebook gradebook join edu_course_offering offering on offering.id=gradebook.offering_id where offering.semester_code=? and offering.campus_id=?",
				semester, organizationId);
		long published = count(
				"select count(*) from edu_gradebook gradebook join edu_course_offering offering on offering.id=gradebook.offering_id where offering.semester_code=? and offering.campus_id=? and gradebook.status='PUBLISHED'",
				semester, organizationId);
		long pendingChanges = count(
				"select count(*) from edu_student_status_change change join edu_student_profile student on student.id=change.student_id join edu_administrative_class class on class.id=student.administrative_class_id where change.status in ('PENDING','APPROVED_PENDING') and class.campus_id=?",
				organizationId);
		long deadOutbox = count("select count(*) from edu_domain_event_outbox where status='DEAD'");
		return Map.of("semesterCode", semester, "courseOfferings", offeringCount, "gradebookCount", gradebooks,
				"publishedGradebooks", published, "unpublishedGradebooks", Math.max(0, gradebooks - published),
				"pendingStudentChanges", pendingChanges, "deadDomainEvents", deadOutbox, "readyToClose",
				offeringCount > 0 && gradebooks > 0 && gradebooks == published && pendingChanges == 0
						&& deadOutbox == 0);
	}

	/**
	 * 考试收口只读检查。考试计划、场次、考场和监考是独立业务表，不能用“有考试计划”
	 * 代替“考试已经具备执行条件”，因此把每一层数量和未完成数量都返回给候选结果。
	 */
	private Map<String, Object> termEndExamCheck(Map<String, Object> config) {
		String semester = required(config.get("semesterCode"), "学期编码");
		String organizationId = required(config.get("organizationId"), "目标机构");
		requireTermOrganization(semester, organizationId);
		long plans = count("select count(*) from edu_exam_plan where semester_code=? and organization_id=?", semester,
				organizationId);
		long publishedPlans = count(
				"select count(*) from edu_exam_plan where semester_code=? and organization_id=? and status='PUBLISHED'",
				semester, organizationId);
		long sessions = count(
				"select count(*) from edu_exam_session session join edu_exam_plan plan on plan.id=session.plan_id where plan.semester_code=? and plan.organization_id=?",
				semester, organizationId);
		long rooms = count(
				"select count(*) from edu_exam_room room join edu_exam_session session on session.id=room.session_id join edu_exam_plan plan on plan.id=session.plan_id where plan.semester_code=? and plan.organization_id=?",
				semester, organizationId);
		long assignedInvigilations = count(
				"select count(*) from edu_exam_invigilation assignment join edu_exam_room room on room.id=assignment.room_id join edu_exam_session session on session.id=room.session_id join edu_exam_plan plan on plan.id=session.plan_id where plan.semester_code=? and plan.organization_id=? and assignment.status='ASSIGNED'",
				semester, organizationId);
		long pendingChanges = count(
				"select count(*) from edu_exam_invigilation_change change join edu_exam_invigilation assignment on assignment.id=change.assignment_id join edu_exam_room room on room.id=assignment.room_id join edu_exam_session session on session.id=room.session_id join edu_exam_plan plan on plan.id=session.plan_id where plan.semester_code=? and plan.organization_id=? and change.status='PENDING'",
				semester, organizationId);
		return Map.of("semesterCode", semester, "examPlans", plans, "publishedExamPlans", publishedPlans,
				"examSessions", sessions, "examRooms", rooms, "assignedInvigilations", assignedInvigilations,
				"pendingInvigilationChanges", pendingChanges, "readyForExamClose", plans > 0 && plans == publishedPlans
						&& sessions > 0 && rooms > 0 && assignedInvigilations > 0 && pendingChanges == 0);
	}

	/** 成绩收口检查单独暴露，便于 Agent 在发现未发布成绩时停在可解释的人工处理节点。 */
	private Map<String, Object> termEndGradeCheck(Map<String, Object> config) {
		String semester = required(config.get("semesterCode"), "学期编码");
		String organizationId = required(config.get("organizationId"), "目标机构");
		requireTermOrganization(semester, organizationId);
		long total = count(
				"select count(*) from edu_gradebook gradebook join edu_course_offering offering on offering.id=gradebook.offering_id where offering.semester_code=? and offering.campus_id=?",
				semester, organizationId);
		long confirmed = count(
				"select count(*) from edu_gradebook gradebook join edu_course_offering offering on offering.id=gradebook.offering_id where offering.semester_code=? and offering.campus_id=? and gradebook.status in ('CONFIRMED','PUBLISHED')",
				semester, organizationId);
		long published = count(
				"select count(*) from edu_gradebook gradebook join edu_course_offering offering on offering.id=gradebook.offering_id where offering.semester_code=? and offering.campus_id=? and gradebook.status='PUBLISHED'",
				semester, organizationId);
		return Map.of("semesterCode", semester, "gradebooks", total, "confirmedGradebooks", confirmed,
				"publishedGradebooks", published, "unconfirmedGradebooks", Math.max(0, total - confirmed),
				"unpublishedGradebooks", Math.max(0, total - published), "readyForGradeClose",
				total > 0 && total == published);
	}

	/** 归档前检查不执行归档写入，避免 Agent 越权改变历史数据。 */
	private Map<String, Object> termEndArchiveCheck(Map<String, Object> config) {
		String semester = required(config.get("semesterCode"), "学期编码");
		String organizationId = required(config.get("organizationId"), "目标机构");
		requireTermOrganization(semester, organizationId);
		long pendingStatusChanges = count(
				"select count(*) from edu_student_status_change change join edu_student_profile student on student.id=change.student_id join edu_administrative_class class on class.id=student.administrative_class_id where change.status in ('PENDING','APPROVED_PENDING') and class.campus_id=?",
				organizationId);
		long deadEvents = count("select count(*) from edu_domain_event_outbox where status='DEAD'");
		long activeOfferings = count(
				"select count(*) from edu_course_offering where semester_code=? and status='ACTIVE' and campus_id=?",
				semester, organizationId);
		return Map.of("semesterCode", semester, "activeOfferings", activeOfferings, "pendingStudentStatusChanges",
				pendingStatusChanges, "deadDomainEvents", deadEvents, "readyForArchive",
				activeOfferings > 0 && pendingStatusChanges == 0 && deadEvents == 0, "message", "归档写入由教务管理员在归档菜单确认后执行");
	}

	private Map<String, Object> prepareGraduation(Map<String, Object> config, String actor) {
		int admissionYear = number(config.get("graduatingAdmissionYear"), number(config.get("admissionYear"), 0));
		if (admissionYear < 2000)
			throw new IllegalArgumentException("必须明确配置毕业年级入学年份");
		int limit = bounded(config.get("batchLimit"), 100, 500);
		LocalDate effectiveDate = date(config.get("effectiveDate"), LocalDate.now());
		String organizationId = required(config.get("organizationId"), "目标机构");
		var targets = students.findAllByOrderByStudentNo().stream()
				.filter(value -> Integer.valueOf(admissionYear).equals(value.getGradeYear()))
				.filter(value -> "ACTIVE".equals(value.getEnrollmentStatus()))
				.filter(value -> administrativeClasses.findById(value.getAdministrativeClassId())
						.map(item -> organizationId.equals(item.getCampusId())).orElse(false))
				.limit(limit).toList();
		// 候选执行只返回可核对名单，绝不调用会创建学籍异动申请的领域写接口。
		if (bool(config.get("dryRun"), false)) {
			List<Map<String, Object>> rows = targets.stream()
					.map(student -> Map.<String, Object>of("studentId", student.getId(), "studentNo",
							student.getStudentNo(), "graduatingAdmissionYear", admissionYear, "effectiveDate",
							effectiveDate.toString()))
					.toList();
			return Map.of("dryRun", true, "valid", true, "graduatingAdmissionYear", admissionYear, "targetCount",
					targets.size(), "candidateCount", rows.size(), "createdRequests", 0, "rows", rows, "message",
					"毕业候选名单已生成，尚未创建正式学籍异动申请");
		}
		int created = 0;
		int skipped = 0;
		List<String> errors = new ArrayList<>();
		for (var student : targets) {
			try {
				studentChanges.request(student.getId(),
						new StudentStatusChangeCommand("GRADUATE", null, effectiveDate, "学期运营智能体生成毕业学籍异动申请"), actor);
				created++;
			} catch (IllegalStateException exception) {
				skipped++;
				errors.add(student.getStudentNo() + "：" + exception.getMessage());
			}
		}
		return Map.of("graduatingAdmissionYear", admissionYear, "effectiveDate", effectiveDate, "targetCount",
				targets.size(), "createdRequests", created, "skipped", skipped, "errors", errors, "valid",
				errors.isEmpty());
	}

	private void requireTermOrganization(String semesterCode, String organizationId) {
		var term = terms.findByTermCode(semesterCode)
				.orElseThrow(() -> new IllegalArgumentException("学期不存在：" + semesterCode));
		if (!organizationId.equals(term.getOrganizationId()))
			throw new IllegalArgumentException("学期不属于当前智能体任务的目标机构");
	}

	private long count(String sql, Object... args) {
		Long value = jdbc.queryForObject(sql, Long.class, args);
		return value == null ? 0 : value;
	}

	private boolean requiresOrganizationScope(String toolCode) {
		return !(toolCode.startsWith("workflow.") || toolCode.startsWith("form.")
				|| "iam.role.upsert".equals(toolCode));
	}

	private String resolveOrganization(Map<String, Object> config, String actor) {
		String reference = text(config.get("organizationId"));
		if (!reference.isBlank()) {
			List<String> ids = jdbc.queryForList(
					"select id from t_organization where status=1 and (id=? or org_code=? or organization_name=?)",
					String.class, reference, reference, reference);
			if (ids.isEmpty())
				throw new IllegalArgumentException("目标机构不存在或已停用：" + reference);
			if (ids.size() > 1)
				throw new IllegalArgumentException("目标机构不唯一，请使用机构编码或 ID");
			dataScopes.requireOrganization(authentication(actor), ids.getFirst());
			return ids.getFirst();
		}
		Set<String> visible = dataScopes.visibleOrganizationIds(authentication(actor));
		if (visible != null && visible.size() == 1)
			return visible.iterator().next();
		throw new IllegalArgumentException("请明确指定本次智能体任务的目标机构");
	}

	private UsernamePasswordAuthenticationToken authentication(String actor) {
		return new UsernamePasswordAuthenticationToken(actor, "", List.of());
	}

	private int bounded(Object value, int fallback, int maximum) {
		return Math.max(1, Math.min(number(value, fallback), maximum));
	}

	private int number(Object value, int fallback) {
		if (value instanceof Number number)
			return number.intValue();
		try {
			return Integer.parseInt(text(value));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private boolean bool(Object value, boolean fallback) {
		if (value instanceof Boolean bool)
			return bool;
		return value == null ? fallback : Boolean.parseBoolean(text(value));
	}

	private LocalDate date(Object value, LocalDate fallback) {
		try {
			return value == null || text(value).isBlank() ? fallback : LocalDate.parse(text(value));
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("日期必须使用 yyyy-MM-dd 格式");
		}
	}

	private LocalDate requiredDate(Object value, String label) {
		if (value == null || text(value).isBlank())
			throw new IllegalArgumentException(label + "不能为空");
		return date(value, null);
	}

	private Set<String> stringSet(Object value) {
		if (!(value instanceof Iterable<?> iterable))
			return Set.of();
		Set<String> result = new LinkedHashSet<>();
		for (Object item : iterable)
			if (!text(item).isBlank())
				result.add(text(item));
		return result;
	}

	private String required(Object value, String label) {
		String result = text(value);
		if (result.isBlank())
			throw new IllegalArgumentException(label + "不能为空");
		return result;
	}

	private String text(Object value) {
		return value == null ? "" : String.valueOf(value).trim();
	}

	private record TeachingAssignmentCandidate(TeacherAcademicProfile teacher,
			com.chronos.education.scheduling.model.Subject subject,
			com.chronos.education.scheduling.model.AdministrativeClass administrativeClass, int weeklyLessons) {
	}
}
