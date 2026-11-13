package com.chronos.education.academic.service;

import com.chronos.commons.model.PageView;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.service.AcademicDataService;
import com.chronos.education.scheduling.service.ClassSchedulingService;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.EducationResourceTransactionLock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TermCoursePlanService {
	private static final String PLAN_ROWS = """
			SELECT plan.id, plan.administrative_class_id, cls.class_name, program.program_name,
			       plan.course_id, course.course_code, course.course_name, course.subject_id,
			       subject.subject_name, course.required_room_type, plan.weekly_lessons, cls.campus_id, offering.id AS offering_id,
			       course.enabled AND cls.status = 'ACTIVE' AS ready
			FROM edu_term_course_plan plan
			JOIN edu_administrative_class cls ON cls.id = plan.administrative_class_id
			JOIN edu_academic_program program ON program.id = plan.program_id
			JOIN edu_course_catalog course ON course.id = plan.course_id
			LEFT JOIN edu_subject subject ON subject.id = course.subject_id
			LEFT JOIN edu_course_offering offering ON offering.term_course_plan_id = plan.id
			WHERE plan.academic_term_id = ?
			""";
	private final JdbcTemplate jdbc;
	private final AcademicTermRepository terms;
	private final EducationDataScopeService scopes;
	private final EducationResourceTransactionLock locks;
	private final StudentProfileRepository students;
	private final ClassSchedulingService scheduling;
	private final AcademicDataService academic;

	public record Command(String classId, String programId, String courseId, Integer weeklyLessons) {}
	public record PlanRow(String id, String classId, String className, String programName, String courseId,
			String courseCode, String courseName, String subjectId, String subjectName, String roomType,
			int weeklyLessons, String campusId, boolean ready, String offeringId) {}
	public record Candidate(String planId, String className, String courseName, String teacherName,
			int studentCount, int weeklyLessons, String status, String revision, String offeringId) {}
	public record Generation(List<Candidate> rows, int created) {}
	private record Teacher(String id, String name) {}

	@Transactional(readOnly = true)
	public List<AcademicTerm> terms(String organizationId, String actor) {
		scopes.assertSchoolAccess(scopes.resolve(actor), organizationId);
		return terms.findByOrganizationIdOrderByStartDateDesc(organizationId);
	}

	@Transactional(readOnly = true)
	public PageView<PlanRow> list(String organizationId, String termCode, String keyword, int page, int size, String actor) {
		var term = term(organizationId, termCode, actor);
		return PageView.from(rows(term, keyword), Math.min(page, 1_000_000), size);
	}

	@Transactional
	public void create(String organizationId, String termCode, Command command, String actor) {
		var term = term(organizationId, termCode, actor);
		lockActiveTerm(term);
		validate(term, command);
		insert(term, command);
	}

	public record CourseOption(String classId, String className, String programId, String programName,
			String courseId, String courseName, int suggestedTermNo) {}

	@Transactional(readOnly = true)
	public List<CourseOption> options(String organizationId, String termCode, String actor) {
		term(organizationId, termCode, actor);
		return jdbc.query("""
				SELECT cls.id AS class_id, cls.class_name, program.id AS program_id, program.program_name,
				       course.id AS course_id, course.course_name, item.suggested_term_no
				FROM edu_administrative_class cls
				JOIN edu_academic_program program ON program.school_id = cls.campus_id AND program.major_id = cls.major_id
				    AND program.cohort_year = cls.grade_year AND program.status = 'PUBLISHED'
				JOIN edu_academic_program_course item ON item.program_id = program.id
				JOIN edu_course_catalog course ON course.id = item.course_id
				WHERE cls.campus_id = ? AND cls.status = 'ACTIVE' AND course.enabled AND course.subject_id IS NOT NULL
				    AND (course.organization_id IS NULL OR course.organization_id = cls.campus_id)
				ORDER BY cls.class_code, program.program_code, item.suggested_term_no, course.course_code
				""", (rs, index) -> new CourseOption(rs.getString("class_id"), rs.getString("class_name"),
						rs.getString("program_id"), rs.getString("program_name"), rs.getString("course_id"),
						rs.getString("course_name"), rs.getInt("suggested_term_no")), organizationId);
	}

	@Transactional
	public void updateHours(String organizationId, String termCode, String id, Integer hours, String actor) {
		var term = term(organizationId, termCode, actor);
		lockActiveTerm(term);
		assertMutable(term, id);
		validateHours(hours);
		jdbc.update("UPDATE edu_term_course_plan SET weekly_lessons = ? WHERE id = ?", hours, id);
	}

	@Transactional
	public void delete(String organizationId, String termCode, String id, String actor) {
		var term = term(organizationId, termCode, actor);
		lockActiveTerm(term);
		assertMutable(term, id);
		jdbc.update("DELETE FROM edu_term_course_plan WHERE id = ?", id);
	}

	private void assertMutable(AcademicTerm term, String id) {
		if (!exists("SELECT EXISTS (SELECT 1 FROM edu_term_course_plan WHERE id = ? AND academic_term_id = ?)",
				id, term.getId())) {
			throw new IllegalArgumentException("当前学期没有该开课计划");
		}
		if (exists("SELECT EXISTS (SELECT 1 FROM edu_course_offering WHERE term_course_plan_id = ?)", id)) {
			throw new IllegalStateException("开课计划已生成教学任务，请先处理关联任务");
		}
	}

	/** 批量校验与写入共用，正式导入在同一事务中重新检查冲突。 */
	@Transactional
	public List<String> importRows(String organizationId, String termCode, List<List<String>> entries,
			boolean dryRun, String actor) {
		var term = term(organizationId, termCode, actor);
		if (dryRun) assertActiveTerm(term);
		else lockActiveTerm(term);
		var commands = new ArrayList<Command>();
		var errors = new ArrayList<String>();
		var keys = new HashSet<List<String>>();
		for (var row : entries) {
			try {
				String classId = lookup("SELECT id FROM edu_administrative_class WHERE campus_id = ? AND class_code = ?",
						"行政班", organizationId, row.get(1));
				String programId = lookup("""
						SELECT id FROM edu_academic_program
						WHERE school_id = ? AND program_code = ? AND status = 'PUBLISHED'
						""", "已发布培养方案", organizationId, row.get(2));
				String courseId = lookup("""
						SELECT id FROM edu_course_catalog WHERE course_code = ? AND (organization_id = ? OR organization_id IS NULL)
						""", "课程", row.get(3), organizationId);
				var command = new Command(classId, programId, courseId, Integer.parseInt(row.get(4)));
				validate(term, command);
				if (!keys.add(List.of(classId, courseId))) throw new IllegalArgumentException("文件内班级与课程重复");
				commands.add(command);
			} catch (NumberFormatException exception) {
				errors.add("第 " + row.getFirst() + " 行：周课时必须是正整数");
			} catch (IllegalArgumentException exception) {
				errors.add("第 " + row.getFirst() + " 行：" + exception.getMessage());
			}
		}
		if (!dryRun && errors.isEmpty()) commands.forEach(command -> insert(term, command));
		return errors;
	}

	@Transactional
	public Generation generate(String organizationId, String termCode, Map<String, String> revisions, boolean dryRun, String actor) {
		var term = term(organizationId, termCode, actor);
		if (dryRun) assertActiveTerm(term);
		else lockActiveTerm(term);
		return generateCandidates(term, revisions, dryRun);
	}

	/** Existing preparation callers keep their result contract and use explicit plans when present. */
	@Transactional
	public Map<String, Object> generateExistingPlans(AcademicTerm term, int limit, boolean dryRun) {
		if (!dryRun) locks.lockSemester(term.getTermCode());
		if (!exists("SELECT EXISTS (SELECT 1 FROM edu_term_course_plan WHERE academic_term_id = ?)", term.getId())) {
			return Map.of();
		}
		assertActiveTerm(term);
		var preview = generateCandidates(term, Map.of(), true).rows();
		var ready = preview.stream().filter(row -> row.revision() != null).limit(limit).toList();
		var result = new LinkedHashMap<String, Object>();
		result.put("dryRun", dryRun);
		result.put("semesterCode", term.getTermCode());
		result.put("source", "TERM_COURSE_PLAN");
		result.put("assignments", preview.size());
		result.put("creatable", ready.size());
		result.put("alreadyExists", preview.stream().filter(row -> "已存在教学任务".equals(row.status())).count());
		result.put("ambiguousCourse", 0);
		result.put("missingContext", preview.stream().filter(row -> row.revision() == null && !"已存在教学任务".equals(row.status())).count());
		result.put("proposals", ready);
		result.put("diagnostics", preview.stream().filter(row -> row.revision() == null).toList());
		var created = List.<String>of();
		if (!dryRun && !ready.isEmpty()) {
			var revisions = ready.stream().collect(Collectors.toMap(Candidate::planId, Candidate::revision));
			created = generateCandidates(term, revisions, false).rows().stream()
					.map(Candidate::offeringId).filter(Objects::nonNull).toList();
		}
		result.put("created", created.size());
		result.put("createdOfferingIds", created);
		return result;
	}

	private Generation generateCandidates(AcademicTerm term, Map<String, String> revisions, boolean dryRun) {
		String termCode = term.getTermCode();
		var selected = revisions == null ? java.util.Set.<String>of() : revisions.keySet();
		var plans = rows(term, null).stream().filter(plan -> selected.isEmpty() || selected.contains(plan.id())).toList();
		if (!selected.isEmpty() && plans.size() != selected.size()) throw new IllegalArgumentException("所选开课计划已变更，请刷新");
		var result = new ArrayList<Candidate>();
		int created = 0;
		for (var plan : plans) {
			var existing = jdbc.query("""
					SELECT id, teacher_name, student_count FROM edu_course_offering
					WHERE term_course_plan_id = ?
					   OR (semester_code = ? AND administrative_class_id = ? AND course_code = ?)
					ORDER BY CASE WHEN term_course_plan_id = ? THEN 0 ELSE 1 END, offering_code
					LIMIT 1
					""", (rs, index) -> new Candidate(plan.id(), plan.className(), plan.courseName(),
							rs.getString("teacher_name"), rs.getInt("student_count"), plan.weeklyLessons(),
							"已存在教学任务", null, rs.getString("id")),
					plan.id(), termCode, plan.classId(), plan.courseCode(), plan.id());
			if (!existing.isEmpty()) {
				result.add(existing.getFirst());
				continue;
			}
			if (!plan.ready()) {
				result.add(candidate(plan, null, 0, "课程或行政班已停用", null));
				continue;
			}
			var members = students.findByAdministrativeClassId(plan.classId()).stream()
					.filter(student -> "ACTIVE".equals(student.getEnrollmentStatus())).toList();
			var teachers = eligibleTeachers(term, plan);
			var issues = new ArrayList<String>(2);
			if (teachers.isEmpty()) issues.add("缺少任课教师");
			if (teachers.size() > 1) issues.add("有多位任课教师，请明确课程任教关系");
			if (members.isEmpty()) issues.add("行政班没有在籍学生");
			var teacher = teachers.size() == 1 ? teachers.getFirst() : null;
			if (!issues.isEmpty()) {
				result.add(candidate(plan, teacher, members.size(), String.join("；", issues), null));
				continue;
			}
			String revision = UUID.nameUUIDFromBytes((plan.toString() + teacher
					+ members.stream().map(member -> member.getId()).sorted().toList()).getBytes(StandardCharsets.UTF_8)).toString();
			if (!dryRun && !revision.equals(revisions.get(plan.id()))) {
				throw new IllegalStateException("教学任务候选已变更，请重新预览");
			}
			String offeringId = null;
			if (!dryRun) {
				var offering = new CourseOffering();
				offering.setSemesterCode(termCode);
				offering.setOfferingCode("TP-" + plan.id());
				offering.setCourseCode(plan.courseCode());
				offering.setCourseName(plan.courseName());
				String className = plan.className() + "-" + plan.courseName();
				offering.setTeachingClassName(className.substring(0, Math.min(128, className.length())));
				offering.setTeacherId(teacher.id());
				offering.setTeacherName(teacher.name());
				offering.setCampusId(plan.campusId());
				offering.setAdministrativeClassId(plan.classId());
				offering.setStudentCount(members.size());
				offering.setWeeklyLessons(plan.weeklyLessons());
				offering.setRequiredRoomType(plan.roomType());
				var saved = scheduling.saveOffering(null, offering);
				saved.setTermCoursePlanId(plan.id());
				offeringId = saved.getId();
				for (var member : members) academic.enrollFromAdministrativeClass(saved.getId(), member.getId());
				created++;
			}
			result.add(candidate(plan, teacher, members.size(), dryRun ? "可生成" : "已生成", revision, offeringId));
		}
		if (!dryRun && created != selected.size()) {
			throw new IllegalStateException("部分候选已无法生成，请重新预览");
		}
		return new Generation(result, created);
	}

	private Candidate candidate(PlanRow plan, Teacher teacher, int count, String status, String revision) {
		return candidate(plan, teacher, count, status, revision, null);
	}

	private Candidate candidate(PlanRow plan, Teacher teacher, int count, String status, String revision, String offeringId) {
		return new Candidate(plan.id(), plan.className(), plan.courseName(), teacher == null ? null : teacher.name(),
				count, plan.weeklyLessons(), status, revision, offeringId);
	}

	private List<Teacher> eligibleTeachers(AcademicTerm term, PlanRow plan) {
		// 有明确课程关系时优先使用。仅按学科登记的关系可作为唯一人选，课程始终来自开课计划。
		return jdbc.query("""
				WITH candidates AS (
				    SELECT DISTINCT teacher.id, teacher.teacher_name, assignment.course_id IS NOT NULL AS specific
				    FROM edu_teacher_teaching_assignment assignment
				    JOIN edu_teacher_profile teacher ON teacher.id = assignment.teacher_id
				    WHERE assignment.academic_term_id = ? AND assignment.administrative_class_id = ?
				      AND assignment.subject_id = ? AND (assignment.course_id = ? OR assignment.course_id IS NULL)
				      AND assignment.enabled AND assignment.assignment_role = 'TEACHER'
				      AND teacher.employment_status = 'ACTIVE' AND teacher.enabled
				)
				SELECT DISTINCT id, teacher_name FROM candidates
				WHERE specific OR NOT EXISTS (SELECT 1 FROM candidates WHERE specific)
				""", (rs, index) -> new Teacher(rs.getString("id"), rs.getString("teacher_name")),
				term.getId(), plan.classId(), plan.subjectId(), plan.courseId());
	}

	private List<PlanRow> rows(AcademicTerm term, String keyword) {
		String pattern = "%" + (keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT))
				.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
		return jdbc.query(PLAN_ROWS + """
				AND lower(concat_ws(' ', cls.class_code, cls.class_name, program.program_name, course.course_code,
				    course.course_name, subject.subject_name)) LIKE ? ESCAPE '!'
				ORDER BY cls.class_code, course.course_code, plan.id
				""", (rs, index) -> new PlanRow(rs.getString("id"), rs.getString("administrative_class_id"),
						rs.getString("class_name"), rs.getString("program_name"), rs.getString("course_id"),
						rs.getString("course_code"), rs.getString("course_name"), rs.getString("subject_id"),
						rs.getString("subject_name"), rs.getString("required_room_type"),
						rs.getInt("weekly_lessons"), rs.getString("campus_id"), rs.getBoolean("ready"), rs.getString("offering_id")),
				term.getId(), pattern);
	}

	private AcademicTerm term(String organizationId, String termCode, String actor) {
		scopes.assertSchoolAccess(scopes.resolve(actor), organizationId);
		return terms.findByTermCode(termCode).filter(term -> organizationId.equals(term.getOrganizationId()))
				.orElseThrow(() -> new IllegalArgumentException("所选机构下没有这个学期"));
	}

	private void lockActiveTerm(AcademicTerm term) {
		locks.lockSemester(term.getTermCode());
		assertActiveTerm(term);
	}

	private void assertActiveTerm(AcademicTerm term) {
		if (!"ACTIVE".equals(term.getStatus())) {
			throw new IllegalArgumentException("已归档学期的开课计划只可查看");
		}
	}

	private void validate(AcademicTerm term, Command command) {
		validateHours(command.weeklyLessons());
		if (!exists("""
				SELECT EXISTS (
				    SELECT 1 FROM edu_administrative_class cls
				    JOIN edu_academic_program program ON program.major_id = cls.major_id AND program.cohort_year = cls.grade_year
				    JOIN edu_academic_program_course item ON item.program_id = program.id
				    JOIN edu_course_catalog course ON course.id = item.course_id
				    WHERE cls.id = ? AND cls.campus_id = ? AND cls.status = 'ACTIVE'
				      AND program.id = ? AND program.school_id = cls.campus_id AND program.status = 'PUBLISHED'
				      AND course.id = ? AND course.enabled AND course.subject_id IS NOT NULL
				      AND (course.organization_id IS NULL OR course.organization_id = cls.campus_id)
				)
				""", command.classId(), term.getOrganizationId(), command.programId(), command.courseId())) {
			throw new IllegalArgumentException("请选择本机构的启用行政班，以及适用的已发布培养方案中已关联学科的启用课程");
		}
		if (exists("""
				SELECT EXISTS (SELECT 1 FROM edu_term_course_plan
				    WHERE academic_term_id = ? AND administrative_class_id = ? AND course_id = ?)
				""", term.getId(), command.classId(), command.courseId())) {
			throw new IllegalArgumentException("该行政班本学期已计划开设这门课程");
		}
	}

	private void validateHours(Integer hours) {
		if (hours == null || hours < 1 || hours > 56) {
			throw new IllegalArgumentException("周课时必须在 1 到 56 节之间");
		}
	}

	private void insert(AcademicTerm term, Command command) {
		jdbc.update("""
				INSERT INTO edu_term_course_plan(id, academic_term_id, administrative_class_id, program_id, course_id, weekly_lessons)
				VALUES (?, ?, ?, ?, ?, ?)
				""", UUID.randomUUID().toString(), term.getId(), command.classId(), command.programId(), command.courseId(),
				command.weeklyLessons());
	}

	private String lookup(String sql, String label, Object... params) {
		var ids = jdbc.queryForList(sql, String.class, params);
		if (ids.size() != 1) throw new IllegalArgumentException("未能唯一匹配" + label + "编码");
		return ids.getFirst();
	}

	private boolean exists(String sql, Object... params) {
		return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, params));
	}
}
