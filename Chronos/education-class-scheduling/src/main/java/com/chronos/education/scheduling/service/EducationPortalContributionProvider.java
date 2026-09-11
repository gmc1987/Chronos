package com.chronos.education.scheduling.service;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.StudentGuardianRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.StudentGuardianRelation;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.portal.spi.PortalContribution;
import com.chronos.portal.spi.PortalContributionProvider;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 为统一门户提供学校概览和当前教师个人课表。 */
@Component
public class EducationPortalContributionProvider implements PortalContributionProvider {
	private final IAdminUserRepository users;
	private final TeacherAcademicProfileRepository teachers;
	private final StudentProfileRepository students;
	private final AdministrativeClassRepository classes;
	private final CourseOfferingRepository offerings;
	private final ClassroomRepository classrooms;
	private final AcademicTermRepository terms;
	private final SchedulePlanVersionService planVersions;
	private final EducationUserBindingRepository bindings;
	private final TeachingClassMemberRepository teachingClassMembers;
	private final StudentGuardianRepository guardians;

	public EducationPortalContributionProvider(
			IAdminUserRepository users,
			TeacherAcademicProfileRepository teachers,
			StudentProfileRepository students,
			AdministrativeClassRepository classes,
			CourseOfferingRepository offerings,
			ClassroomRepository classrooms,
			AcademicTermRepository terms,
			SchedulePlanVersionService planVersions,
			EducationUserBindingRepository bindings,
			TeachingClassMemberRepository teachingClassMembers,
			StudentGuardianRepository guardians) {
		this.users = users;
		this.teachers = teachers;
		this.students = students;
		this.classes = classes;
		this.offerings = offerings;
		this.classrooms = classrooms;
		this.terms = terms;
		this.planVersions = planVersions;
		this.bindings = bindings;
		this.teachingClassMembers = teachingClassMembers;
		this.guardians = guardians;
	}

	@Override
	public String providerCode() {
		return "DATA";
	}

	@Override
	@Transactional(readOnly = true)
	public PortalContribution load(String username) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("metrics", List.of(
				metric("教师", teachers.count()),
				metric("学生", students.count()),
				metric("班级", classes.count()),
				metric("教学任务", offerings.count())));
		data.put("allRoute", "/portal/education/schedule");
		terms.findFirstByCurrentTermTrueAndStatusOrderByStartDateDesc("ACTIVE")
				.ifPresent(term -> loadPersonalSchedule(
						username,
						term.getTermCode(),
						term.getTermName(),
						data));
		loadTeacherSummary(username, data);
		return new PortalContribution(providerCode(), true, "ok", data);
	}

	private void loadTeacherSummary(String username, Map<String, Object> data) {
		var account = users.findByUsername(username);
		if (account == null || account.getEmployeeId() == null) {
			return;
		}
		teachers.findByEmployeeId(account.getEmployeeId()).ifPresent(teacher -> {
			long courseCount = offerings.findByTeacherIdOrderByOfferingCode(teacher.getId()).size();
			data.put("teacherSummary", Map.of(
					"teacherName", teacher.getTeacherName(),
					"maxWeeklyLessons", teacher.getMaxWeeklyLessons() == null ? 0 : teacher.getMaxWeeklyLessons(),
					"teachingCourseCount", courseCount,
					"leaveStatus", "暂无待处理请假流程",
					"notificationStatus", "暂无未读通知",
					"aiDescription", "基于个人课表和任教关系生成教务分析"));
		});
	}

	private void loadPersonalSchedule(
			String username,
			String semesterCode,
			String termName,
			Map<String, Object> data) {
		Map<String, CourseOffering> byId = offerings
				.findBySemesterCodeOrderByOfferingCode(semesterCode)
				.stream()
				.collect(Collectors.toMap(CourseOffering::getId, value -> value));
		Map<String, Classroom> classroomById = classrooms.findAll().stream()
				.collect(Collectors.toMap(Classroom::getId, value -> value));
		Set<String> allowedOfferingIds = resolvePersonalOfferingIds(username, byId);
		data.put("termName", termName);
		// 门户只消费最近发布版本，防止排课员正在编辑的草稿提前对用户可见。
		data.put("mySchedule", planVersions.latestPublishedEntries(semesterCode).stream()
				.filter(entry -> !"CANCELLED".equals(entry.getStatus()))
				.filter(entry -> allowedOfferingIds.contains(entry.getOfferingId()))
				.map(entry -> scheduleItem(
						entry,
						byId.get(entry.getOfferingId()),
						classroomById.get(entry.getClassroomId())))
				.toList());
	}

	private Set<String> resolvePersonalOfferingIds(
			String username,
			Map<String, CourseOffering> byId) {
		Set<String> result = new java.util.HashSet<>();
		List<EducationUserBinding> activeBindings = bindings
				.findByUsernameAndStatusOrderByProfileType(username, "ACTIVE");
		for (EducationUserBinding binding : activeBindings) {
			switch (binding.getProfileType()) {
				case "TEACHER" -> byId.values().stream()
						.filter(item -> binding.getProfileId().equals(item.getTeacherId()))
						.map(CourseOffering::getId)
						.forEach(result::add);
				case "STUDENT" -> addStudentOfferings(binding.getProfileId(), result);
				case "PARENT" -> guardians.findByParentIdOrderByCreateTime(binding.getProfileId()).stream()
						.map(item -> item.getStudentId())
						.forEach(studentId -> addStudentOfferings(studentId, result));
				default -> {
					// 数据库约束会拒绝未知类型；这里容错以避免单条脏数据拖垮整个门户。
				}
			}
		}

		// 兼容已有教师账号；迁移到绑定表前仍可通过 IAM employeeId 使用个人课表。
		var account = users.findByUsername(username);
		if (account != null && account.getEmployeeId() != null) {
			teachers.findByEmployeeId(account.getEmployeeId()).ifPresent(teacher -> byId.values().stream()
					.filter(item -> teacher.getId().equals(item.getTeacherId())
							|| teacher.getEmployeeId().equals(item.getTeacherId()))
					.map(CourseOffering::getId)
					.forEach(result::add));
		}
		return result;
	}

	/**
	 * 返回当前账号可以切换查看的学生身份。响应只包含课表选择所需字段，
	 * 不向家长门户暴露手机号、性别等学生隐私信息。
	 */
	@Transactional(readOnly = true)
	public List<Map<String, Object>> studentContexts(String username) {
		return studentContextViews(resolveStudentContexts(username));
	}

	/** 根据账号和可选学生身份返回最近发布课表，禁止横向查询其他学生。 */
	@Transactional(readOnly = true)
	public Map<String, Object> personalSchedule(
			String username,
			String requestedStudentId) {
		Map<String, StudentContext> contexts = resolveStudentContexts(username);
		String selectedStudentId = selectStudentId(contexts, requestedStudentId);
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("selectedStudentId", selectedStudentId == null ? "" : selectedStudentId);
		data.put("studentContexts", studentContextViews(contexts));

		terms.findFirstByCurrentTermTrueAndStatusOrderByStartDateDesc("ACTIVE")
				.ifPresentOrElse(term -> {
					Map<String, CourseOffering> byId = offerings
							.findBySemesterCodeOrderByOfferingCode(term.getTermCode())
							.stream()
							.collect(Collectors.toMap(CourseOffering::getId, value -> value));
					Map<String, Classroom> classroomById = classrooms.findAll().stream()
							.collect(Collectors.toMap(Classroom::getId, value -> value));
					Set<String> allowedOfferingIds = selectedStudentId == null
							? resolvePersonalOfferingIds(username, byId)
							: studentOfferingIds(selectedStudentId);
					data.put("termName", term.getTermName());
					data.put("schedule", planVersions.latestPublishedEntries(term.getTermCode()).stream()
							.filter(entry -> !"CANCELLED".equals(entry.getStatus()))
							.filter(entry -> allowedOfferingIds.contains(entry.getOfferingId()))
							.map(entry -> scheduleItem(
									entry,
									byId.get(entry.getOfferingId()),
									classroomById.get(entry.getClassroomId())))
							.toList());
				}, () -> {
					data.put("termName", "");
					data.put("schedule", List.of());
				});
		return data;
	}

	private Map<String, StudentContext> resolveStudentContexts(String username) {
		Map<String, StudentContext> contexts = new LinkedHashMap<>();
		List<EducationUserBinding> activeBindings = bindings
				.findByUsernameAndStatusOrderByProfileType(username, "ACTIVE");
		for (EducationUserBinding binding : activeBindings) {
			if ("STUDENT".equals(binding.getProfileType())) {
				students.findById(binding.getProfileId()).ifPresent(student -> contexts.put(
						student.getId(),
						new StudentContext(student, "本人", true)));
			}
			if ("PARENT".equals(binding.getProfileType())) {
				for (StudentGuardianRelation relation : guardians
						.findByParentIdOrderByCreateTime(binding.getProfileId())) {
					students.findById(relation.getStudentId()).ifPresent(student -> contexts.put(
							student.getId(),
							new StudentContext(
									student,
									relation.getRelationship(),
									Boolean.TRUE.equals(relation.getPrimaryGuardian()))));
				}
			}
		}
		return contexts;
	}

	private List<Map<String, Object>> studentContextViews(
			Map<String, StudentContext> contexts) {
		return contexts.values().stream()
				.sorted(studentContextComparator())
				.map(context -> Map.<String, Object>of(
						"studentId", context.student().getId(),
						"studentNo", context.student().getStudentNo(),
						"studentName", context.student().getStudentName(),
						"relationship", context.relationship(),
						"primary", context.primary()))
				.toList();
	}

	private Comparator<StudentContext> studentContextComparator() {
		return Comparator.comparing(StudentContext::primary)
				.reversed()
				.thenComparing(context -> context.student().getStudentNo())
				.thenComparing(context -> context.student().getId());
	}

	private String selectStudentId(
			Map<String, StudentContext> contexts,
			String requestedStudentId) {
		if (requestedStudentId != null && !requestedStudentId.isBlank()) {
			if (!contexts.containsKey(requestedStudentId)) {
				throw new AccessDeniedException("无权查看该学生课表");
			}
			return requestedStudentId;
		}
		return contexts.values().stream()
				.sorted(studentContextComparator())
				.map(context -> context.student().getId())
				.findFirst()
				.orElse(null);
	}

	private Set<String> studentOfferingIds(String studentId) {
		Set<String> result = new java.util.HashSet<>();
		addStudentOfferings(studentId, result);
		return result;
	}

	private record StudentContext(
			StudentProfile student,
			String relationship,
			boolean primary) {
	}

	private void addStudentOfferings(String studentId, Set<String> target) {
		teachingClassMembers.findByStudentIdAndEnrollmentStatus(studentId, "ENROLLED").stream()
				.map(item -> item.getOfferingId())
				.forEach(target::add);
	}

	private Map<String, Object> metric(String label, long value) {
		return Map.of("label", label, "value", value);
	}

	private Map<String, Object> scheduleItem(
			ScheduleEntry entry,
			CourseOffering offering,
			Classroom classroom) {
		return Map.of(
				"id", entry.getId(),
				"courseName", offering == null ? "未知课程" : offering.getCourseName(),
				"teachingClassName", offering == null ? "" : offering.getTeachingClassName(),
				"classroomName", classroom == null ? "" : classroom.getRoomName(),
				"dayOfWeek", entry.getDayOfWeek(),
				"periodNo", entry.getPeriodNo(),
				"durationPeriods", entry.getDurationPeriods(),
				"status", entry.getStatus());
	}
}
