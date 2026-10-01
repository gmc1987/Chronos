package com.chronos.education.scheduling.service;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.StudentGuardianRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.StudentStatusChangeRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.education.scheduling.model.StudentGuardianRelation;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.StudentStatusChange;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 聚合班主任日常所需的班级、学生、监护关系和学籍待办，不复制领域主数据。 */
@Service
public class HeadTeacherWorkbenchService {
	private final IAdminUserRepository users;
	private final TeacherAcademicProfileRepository teachers;
	private final EducationUserBindingRepository bindings;
	private final AdministrativeClassRepository classes;
	private final StudentProfileRepository students;
	private final StudentGuardianRepository guardians;
	private final StudentStatusChangeRepository statusChanges;

	public HeadTeacherWorkbenchService(
			IAdminUserRepository users,
			TeacherAcademicProfileRepository teachers,
			EducationUserBindingRepository bindings,
			AdministrativeClassRepository classes,
			StudentProfileRepository students,
			StudentGuardianRepository guardians,
			StudentStatusChangeRepository statusChanges) {
		this.users = users;
		this.teachers = teachers;
		this.bindings = bindings;
		this.classes = classes;
		this.students = students;
		this.guardians = guardians;
		this.statusChanges = statusChanges;
	}

	@Transactional(readOnly = true)
	public List<Map<String, Object>> classes(String username) {
		return headTeacherClasses(username).stream()
				.map(this::classSummary)
				.toList();
	}

	@Transactional(readOnly = true)
	public Map<String, Object> classDetail(String username, String classId) {
		AdministrativeClass administrativeClass = headTeacherClasses(username).stream()
				.filter(item -> item.getId().equals(classId))
				.findFirst()
				.orElseThrow(() -> new AccessDeniedException("无权查看该班级工作台"));
		List<StudentProfile> classStudents = students.findByAdministrativeClassId(classId);
		List<String> studentIds = classStudents.stream()
				.map(StudentProfile::getId)
				.toList();
		Map<String, List<StudentGuardianRelation>> guardianByStudent = studentIds.isEmpty()
				? Map.of()
				: guardians.findByStudentIdIn(studentIds).stream()
						.collect(Collectors.groupingBy(StudentGuardianRelation::getStudentId));
		List<StudentStatusChange> pendingChanges = studentIds.isEmpty()
				? List.of()
				: statusChanges.findTop20ByStudentIdInAndStatusOrderByRequestedAtDesc(
						studentIds,
						"PENDING");
		Map<String, StudentProfile> studentById = classStudents.stream()
				.collect(Collectors.toMap(StudentProfile::getId, Function.identity()));

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("classInfo", classInfo(administrativeClass));
		result.put("metrics", metrics(classStudents, guardianByStudent, pendingChanges.size()));
		result.put("students", classStudents.stream()
				.sorted(Comparator.comparing(StudentProfile::getStudentNo))
				.map(student -> studentItem(
						student,
						guardianByStudent.getOrDefault(student.getId(), List.of())))
				.toList());
		result.put("pendingChanges", pendingChanges.stream()
				.map(change -> changeItem(change, studentById.get(change.getStudentId())))
				.toList());
		return result;
	}

	private List<AdministrativeClass> headTeacherClasses(String username) {
		Set<String> teacherIds = resolveTeacherIds(username);
		if (teacherIds.isEmpty()) {
			return List.of();
		}
		List<AdministrativeClass> result = new ArrayList<>();
		teacherIds.forEach(id -> result.addAll(classes.findByHeadTeacherId(id)));
		return result.stream()
				.collect(Collectors.toMap(
						AdministrativeClass::getId,
						Function.identity(),
						(left, right) -> left,
						LinkedHashMap::new))
				.values()
				.stream()
				.sorted(Comparator.comparing(AdministrativeClass::getClassCode))
				.toList();
	}

	private Set<String> resolveTeacherIds(String username) {
		Set<String> result = bindings
				.findByUsernameAndStatusOrderByProfileType(username, "ACTIVE")
				.stream()
				.filter(item -> "TEACHER".equals(item.getProfileType()))
				.map(EducationUserBinding::getProfileId)
				.collect(Collectors.toSet());
		var account = users.findByUsername(username);
		if (account != null && account.getEmployeeId() != null) {
			teachers.findByEmployeeId(account.getEmployeeId())
					.map(value -> value.getId())
					.ifPresent(result::add);
		}
		return result;
	}

	private Map<String, Object> classSummary(AdministrativeClass value) {
		List<StudentProfile> values = students.findByAdministrativeClassId(value.getId());
		List<String> studentIds = values.stream().map(StudentProfile::getId).toList();
		long guardianCovered = studentIds.isEmpty()
				? 0
				: guardians.countStudentsWithGuardian(studentIds);
		long pending = studentIds.isEmpty()
				? 0
				: statusChanges.countByStudentIdInAndStatus(studentIds, "PENDING");
		Map<String, Object> result = new LinkedHashMap<>(classInfo(value));
		result.put("studentCount", values.size());
		result.put("activeStudentCount", values.stream()
				.filter(item -> "ACTIVE".equals(item.getEnrollmentStatus()))
				.count());
		result.put("guardianCoveredCount", guardianCovered);
		result.put("pendingChangeCount", pending);
		return result;
	}

	private Map<String, Object> classInfo(AdministrativeClass value) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("id", value.getId());
		result.put("classCode", value.getClassCode());
		result.put("className", value.getClassName());
		result.put("gradeYear", value.getGradeYear());
		result.put("gradeId", value.getGradeId());
		result.put("majorId", value.getMajorId());
		result.put("campusId", value.getCampusId());
		result.put("status", value.getStatus());
		return result;
	}

	private Map<String, Object> metrics(
			List<StudentProfile> values,
			Map<String, List<StudentGuardianRelation>> guardianByStudent,
			int pendingCount) {
		long active = values.stream()
				.filter(item -> "ACTIVE".equals(item.getEnrollmentStatus()))
				.count();
		return Map.of(
				"studentCount", values.size(),
				"activeStudentCount", active,
				"nonActiveStudentCount", values.size() - active,
				"guardianMissingCount", values.stream()
						.filter(item -> !guardianByStudent.containsKey(item.getId()))
						.count(),
				"pendingChangeCount", pendingCount);
	}

	private Map<String, Object> studentItem(
			StudentProfile student,
			List<StudentGuardianRelation> studentGuardians) {
		return Map.of(
				"id", student.getId(),
				"studentNo", student.getStudentNo(),
				"studentName", student.getStudentName(),
				"gender", student.getGender() == null ? "" : student.getGender(),
				"enrollmentStatus", student.getEnrollmentStatus(),
				"guardianCount", studentGuardians.size(),
				"hasPrimaryGuardian", studentGuardians.stream()
						.anyMatch(item -> Boolean.TRUE.equals(item.getPrimaryGuardian())));
	}

	private Map<String, Object> changeItem(
			StudentStatusChange change,
			StudentProfile student) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("id", change.getId());
		result.put("studentId", change.getStudentId());
		result.put("studentNo", student == null ? "" : student.getStudentNo());
		result.put("studentName", student == null ? "未知学生" : student.getStudentName());
		result.put("changeType", change.getChangeType());
		result.put("effectiveDate", change.getEffectiveDate());
		result.put("reason", change.getReason());
		result.put("requestedAt", change.getRequestedAt());
		return result;
	}
}
