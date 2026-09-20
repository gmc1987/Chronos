package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.StudentGuardianRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.dao.TeacherTeachingAssignmentRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.StudentGuardianRelation;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.chronos.education.scheduling.model.TeacherTeachingAssignment;
import com.chronos.education.scheduling.model.TeachingClassMember;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.TeacherTimeConstraint;
import com.chronos.model.vo.DataScopeContext;
import com.chronos.Idao.IOrganizationRepository;
import com.chronos.model.pojo.Organization;
import com.chronos.service.iService.IDataScopeService;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 把 IAM 的行业无关范围转换为教育资源范围。
 *
 * <p>本班级来自班主任关系和任教关系；本年级来自任教范围；本教研组使用
 * 教师档案的部门作为当前阶段的教研组边界。后续引入独立 SubjectGroup
 * 实体时，只需替换这里的解析，不需要改变 IAM 数据结构。</p>
 */
@Service
@Transactional(readOnly = true)
public class EducationDataScopeService {
	private static final String CLASS_RESOURCE = "EDUCATION_CLASS";
	private static final String GRADE_RESOURCE = "EDUCATION_GRADE";
	private final IDataScopeService platformScopes;
	private final TeacherAcademicProfileRepository teachers;
	private final TeacherTeachingAssignmentRepository assignments;
	private final AdministrativeClassRepository classes;
	private final StudentProfileRepository students;
	private final StudentGuardianRepository guardians;
	private final EducationUserBindingRepository bindings;
	private final CourseOfferingRepository offerings;
	private final TeachingClassMemberRepository members;
	private final ClassroomRepository classrooms;
	private final ScheduleEntryRepository scheduleEntries;
	private final IOrganizationRepository organizations;

	@Autowired
	public EducationDataScopeService(
			IDataScopeService platformScopes,
			TeacherAcademicProfileRepository teachers,
			TeacherTeachingAssignmentRepository assignments,
			AdministrativeClassRepository classes,
			StudentProfileRepository students,
			StudentGuardianRepository guardians,
			EducationUserBindingRepository bindings,
			CourseOfferingRepository offerings,
			TeachingClassMemberRepository members,
			ClassroomRepository classrooms,
			ScheduleEntryRepository scheduleEntries,
			IOrganizationRepository organizations) {
		this.platformScopes = platformScopes;
		this.teachers = teachers;
		this.assignments = assignments;
		this.classes = classes;
		this.students = students;
		this.guardians = guardians;
		this.bindings = bindings;
		this.offerings = offerings;
		this.members = members;
		this.classrooms = classrooms;
		this.scheduleEntries = scheduleEntries;
		this.organizations = organizations;
	}

	public EducationDataScopeService(
			IDataScopeService platformScopes,
			TeacherAcademicProfileRepository teachers,
			TeacherTeachingAssignmentRepository assignments,
			AdministrativeClassRepository classes,
			StudentProfileRepository students,
			StudentGuardianRepository guardians,
			CourseOfferingRepository offerings,
			ClassroomRepository classrooms,
			ScheduleEntryRepository scheduleEntries) {
		this(platformScopes, teachers, assignments, classes, students, guardians, null,
				offerings, null, classrooms, scheduleEntries, null);
	}

	public EducationDataScopeService(
			IDataScopeService platformScopes,
			TeacherAcademicProfileRepository teachers,
			TeacherTeachingAssignmentRepository assignments,
			AdministrativeClassRepository classes,
			StudentProfileRepository students,
			StudentGuardianRepository guardians,
			EducationUserBindingRepository bindings,
			CourseOfferingRepository offerings,
			TeachingClassMemberRepository members,
			ClassroomRepository classrooms,
			ScheduleEntryRepository scheduleEntries) {
		this(platformScopes, teachers, assignments, classes, students, guardians, bindings,
				offerings, members, classrooms, scheduleEntries, null);
	}

	public EducationDataScope resolve(String username) {
		DataScopeContext platform = platformScopes.resolve(username);
		if (platform.fullAccess()) {
			return new EducationDataScope(
					true,
					Set.of(),
					Set.of(),
					Set.of(),
					Set.of(),
					Set.of(),
					Set.of());
		}
		Set<String> schoolIds = new HashSet<>();
		Set<String> campusIds = new HashSet<>();
		for (String organizationId : platform.organizationIds()) {
			Organization organization = organizations == null
					? null
					: organizations.findById(organizationId).orElse(null);
			if (organization == null) {
				schoolIds.add(organizationId);
				campusIds.add(organizationId);
			} else if ("CAMPUS".equals(organization.getOrganizationType())) {
				campusIds.add(organizationId);
				if (organization != null && organization.getParentOrgId() != null) {
					schoolIds.add(organization.getParentOrgId().getId());
				}
			} else {
				schoolIds.add(organizationId);
				campusIds.addAll(organizations.findByParentOrgId_Id(organizationId).stream()
						.filter(child -> "CAMPUS".equals(child.getOrganizationType()))
						.map(Organization::getId)
						.toList());
			}
		}
		Set<String> gradeIds = new HashSet<>(platform.resourceIds()
				.getOrDefault(GRADE_RESOURCE, Set.of()));
		Set<String> classIds = new HashSet<>(platform.resourceIds()
				.getOrDefault(CLASS_RESOURCE, Set.of()));
		Set<String> teacherIds = new HashSet<>();
		Set<String> studentIds = new HashSet<>();
		(bindings == null ? List.<EducationUserBinding>of()
				: bindings.findByUsernameAndStatusOrderByProfileType(username, "ACTIVE")).stream()
				.filter(binding -> "STUDENT".equals(binding.getProfileType()))
				.map(EducationUserBinding::getProfileId)
				.forEach(studentIds::add);
		if (!campusIds.isEmpty()) {
			classIds.addAll(classes.findByCampusIdIn(campusIds.stream().toList()).stream()
					.map(AdministrativeClass::getId)
					.toList());
		}
		TeacherAcademicProfile currentTeacher = platform.employeeId() == null
				? null
				: teachers.findByEmployeeId(platform.employeeId()).orElse(null);
		if (currentTeacher == null) {
			return new EducationDataScope(
					false,
					Set.copyOf(schoolIds),
					Set.copyOf(campusIds),
					Set.copyOf(gradeIds),
					Set.copyOf(classIds),
					Set.copyOf(teacherIds),
					Set.copyOf(studentIds));
		}
		teacherIds.add(currentTeacher.getId());
		Set<String> scopeTypes = platform.scopeTypes();
		if (scopeTypes.contains("EDUCATION_SUBJECT_GROUP")
				|| scopeTypes.contains("DEPARTMENT")
				|| scopeTypes.contains("DEPARTMENT_AND_CHILDREN")) {
			List<TeacherAcademicProfile> groupTeachers = currentTeacher.getDepartmentId() == null
					? List.of(currentTeacher)
					: teachers.findByDepartmentIdAndEnabledTrueOrderByTeacherNo(
							currentTeacher.getDepartmentId());
			teacherIds.addAll(groupTeachers.stream()
					.map(TeacherAcademicProfile::getId)
					.toList());
		}
		List<TeacherTeachingAssignment> teachingScopes = assignments
				.findByTeacherIdInAndEnabledTrue(teacherIds.stream().toList());
		if (scopeTypes.contains("EDUCATION_CLASS")
				|| scopeTypes.contains("EDUCATION_SUBJECT_GROUP")) {
			classIds.addAll(teachingScopes.stream()
					.map(TeacherTeachingAssignment::getAdministrativeClassId)
					.filter(value -> value != null && !value.isBlank())
					.toList());
			for (String teacherId : teacherIds) {
				classIds.addAll(classes.findByHeadTeacherId(teacherId).stream()
						.map(AdministrativeClass::getId)
						.toList());
			}
		}
		if (scopeTypes.contains("EDUCATION_GRADE")
				|| scopeTypes.contains("EDUCATION_SUBJECT_GROUP")) {
			gradeIds.addAll(teachingScopes.stream()
					.map(TeacherTeachingAssignment::getGradeId)
					.filter(value -> value != null && !value.isBlank())
					.toList());
		}
		return new EducationDataScope(
				false,
				Set.copyOf(schoolIds),
				Set.copyOf(campusIds),
				Set.copyOf(gradeIds),
				Set.copyOf(classIds),
				Set.copyOf(teacherIds),
				Set.copyOf(studentIds));
	}

	/** 防止调用方绕过列表过滤后，凭已知主键读取或修改其他班级数据。 */
	public void assertClassAccess(EducationDataScope scope, String classId) {
		AdministrativeClass administrativeClass = classes.findById(classId)
				.orElseThrow(() -> new IllegalArgumentException("行政班不存在"));
		if (scope.fullAccess()
				|| scope.administrativeClassIds().contains(classId)
				|| scope.gradeIds().contains(administrativeClass.getGradeId())
				|| scope.campusIds().contains(administrativeClass.getCampusId())) {
			return;
		}
		throw new AccessDeniedException("无权访问该班级数据");
	}

	public void assertClassUpdate(
			EducationDataScope scope,
			String classId,
			String targetGradeId) {
		AdministrativeClass current = classes.findById(classId)
				.orElseThrow(() -> new IllegalArgumentException("行政班不存在"));
		assertClassAccess(scope, classId);
		if (!scope.fullAccess()
				&& !Objects.equals(current.getGradeId(), targetGradeId)
				&& !scope.gradeIds().contains(targetGradeId)) {
			throw new AccessDeniedException("无权将班级移动到该年级");
		}
	}

	/** 学生写操作同时校验原记录和目标班级，避免通过转班字段越权。 */
	public void assertStudentAccess(EducationDataScope scope, String studentId) {
		StudentProfile student = students.findById(studentId)
				.orElseThrow(() -> new IllegalArgumentException("学生不存在"));
		if (canAccessStudent(scope, student)) {
			return;
		}
		throw new AccessDeniedException("无权访问该学生数据");
	}

	public boolean canAccessStudent(EducationDataScope scope, StudentProfile student) {
		return scope.fullAccess()
				|| scope.studentIds().contains(student.getId())
				|| scope.administrativeClassIds().contains(student.getAdministrativeClassId())
				|| scope.gradeIds().contains(student.getGradeId());
	}

	public boolean canAccessStudent(EducationDataScope scope, String studentId) {
		return studentId != null && students.findById(studentId)
				.map(student -> canAccessStudent(scope, student))
				.orElse(false);
	}

	public void assertGuardianAccess(EducationDataScope scope, String relationId) {
		StudentGuardianRelation relation = guardians.findById(relationId)
				.orElseThrow(() -> new IllegalArgumentException("监护关系不存在"));
		assertStudentAccess(scope, relation.getStudentId());
	}

	public List<StudentGuardianRelation> visibleGuardians(
			EducationDataScope scope,
			List<StudentGuardianRelation> relations) {
		if (scope.fullAccess()) {
			return relations;
		}
		return relations.stream()
				.filter(relation -> students.findById(relation.getStudentId())
						.map(student -> canAccessStudent(scope, student))
						.orElse(false))
				.toList();
	}

	public List<TeachingClassMember> visibleMembers(
			EducationDataScope scope,
			List<TeachingClassMember> classMembers) {
		if (scope.fullAccess()) {
			return classMembers;
		}
		return classMembers.stream()
				.filter(member -> students.findById(member.getStudentId())
						.map(student -> canAccessStudent(scope, student))
						.orElse(false))
				.toList();
	}

	public void assertTeachingAssignmentAccess(
			EducationDataScope scope,
			TeacherTeachingAssignment assignment) {
		if (scope.fullAccess()
				|| scope.teacherIds().contains(assignment.getTeacherId())
				|| scope.administrativeClassIds().contains(
						assignment.getAdministrativeClassId())
				|| scope.gradeIds().contains(assignment.getGradeId())) {
			return;
		}
		throw new AccessDeniedException("无权访问该任教关系");
	}

	public void assertTeachingAssignmentAccess(
			EducationDataScope scope,
			String assignmentId) {
		TeacherTeachingAssignment assignment = assignments.findById(assignmentId)
				.orElseThrow(() -> new IllegalArgumentException("任教关系不存在"));
		assertTeachingAssignmentAccess(scope, assignment);
	}

	public void assertTeacherAccess(EducationDataScope scope, String teacherId) {
		if (canAccessTeacher(scope, teacherId)) {
			return;
		}
		throw new AccessDeniedException("无权访问该教师数据");
	}

	public boolean canAccessTeacher(EducationDataScope scope, String teacherId) {
		return scope.fullAccess()
				|| scope.teacherIds().contains(teacherId)
				|| !scope.campusIds().isEmpty()
				&& offerings.existsByTeacherIdAndCampusIdIn(
						teacherId,
						scope.campusIds().stream().toList());
	}

	public void assertFullAccess(EducationDataScope scope) {
		if (!scope.fullAccess()) {
			throw new AccessDeniedException("该操作需要全校数据权限");
		}
	}

	public void assertSchoolAccess(EducationDataScope scope, String schoolId) {
		if (scope.fullAccess() || scope.schoolIds().contains(schoolId)) {
			return;
		}
		throw new AccessDeniedException("无权访问该学校督导数据");
	}

	public void assertCampusAccess(EducationDataScope scope, String campusId) {
		if (scope.fullAccess() || scope.campusIds().contains(campusId)) {
			return;
		}
		throw new AccessDeniedException("无权访问该校区督导数据");
	}

	public String requireSingleSchool(EducationDataScope scope) {
		if (scope.fullAccess() || scope.schoolIds().size() != 1) {
			throw new AccessDeniedException("当前数据范围无法确定唯一学校");
		}
		return scope.schoolIds().iterator().next();
	}

	public String requireSchoolForCampus(EducationDataScope scope, String campusId) {
		if (!scope.fullAccess()) {
			assertCampusAccess(scope, campusId);
			return requireSingleSchool(scope);
		}
		if (organizations != null) {
			return organizations.findById(campusId)
					.map(Organization::getParentOrgId)
					.map(Organization::getId)
					.orElseThrow(() -> new AccessDeniedException("校区未关联学校"));
		}
		throw new AccessDeniedException("当前数据范围无法解析学校");
	}

	public List<CourseOffering> visibleOfferings(
			EducationDataScope scope,
			List<CourseOffering> values) {
		if (scope.fullAccess()) {
			return values;
		}
		return values.stream()
				.filter(value -> canAccessOffering(scope, value))
				.toList();
	}

	public List<Classroom> visibleClassrooms(
			EducationDataScope scope,
			List<Classroom> values) {
		if (scope.fullAccess()) {
			return values;
		}
		return values.stream()
				.filter(value -> value.getCampusId() != null
						&& scope.campusIds().contains(value.getCampusId()))
				.toList();
	}

	public List<TeacherTimeConstraint> visibleTeacherConstraints(
			EducationDataScope scope,
			List<TeacherTimeConstraint> values) {
		if (scope.fullAccess()) {
			return values;
		}
		return values.stream()
				.filter(value -> scope.teacherIds().contains(value.getTeacherId()))
				.toList();
	}

	public void assertOfferingAccess(EducationDataScope scope, String offeringId) {
		CourseOffering offering = offerings.findById(offeringId)
				.orElseThrow(() -> new IllegalArgumentException("教学任务不存在"));
		assertOfferingAccess(scope, offering);
	}

	public void assertOfferingAccess(EducationDataScope scope, CourseOffering offering) {
		if (scope.fullAccess() || canAccessOffering(scope, offering)) {
			return;
		}
		throw new AccessDeniedException("无权访问该教学任务");
	}

	public void assertCourseAccess(EducationDataScope scope, String courseId) {
		if (courseId == null || courseId.isBlank()) {
			assertFullAccess(scope);
			return;
		}

		if (scope.fullAccess() || offerings.findByCourseCode(courseId).stream()
				.anyMatch(offering -> canAccessOffering(scope, offering))) return;
		throw new AccessDeniedException("无权访问该课程数据");
	}

	/**
	 * Terms are only visible when at least one real offering in the term is
	 * visible to the caller. This prevents term/calendar endpoints from
	 * becoming an unscoped back door into another campus.
	 */
	public void assertTermAccess(EducationDataScope scope, String semesterCode) {
		if (scope.fullAccess()) return;
		if (semesterCode == null || semesterCode.isBlank()
				|| visibleOfferings(scope, offerings.findBySemesterCodeOrderByOfferingCode(semesterCode)).isEmpty()) {
			throw new AccessDeniedException("无权访问该学期数据");
		}
	}

	public boolean canAccessCourse(EducationDataScope scope, String courseId) {
		return courseId != null && (scope.fullAccess()
				|| offerings.findByCourseCode(courseId).stream()
				.anyMatch(offering -> canAccessOffering(scope, offering)));
	}

	public void assertClassroomAccess(EducationDataScope scope, String classroomId) {
		Classroom classroom = classrooms.findById(classroomId)
				.orElseThrow(() -> new IllegalArgumentException("教室不存在"));
		assertClassroomAccess(scope, classroom);
	}

	public void assertClassroomAccess(EducationDataScope scope, Classroom classroom) {
		if (scope.fullAccess()
				|| classroom.getCampusId() != null
				&& scope.campusIds().contains(classroom.getCampusId())) {
			return;
		}
		throw new AccessDeniedException("无权访问该教室");
	}

	public void assertScheduleEntryAccess(EducationDataScope scope, String entryId) {
		ScheduleEntry entry = scheduleEntries.findById(entryId)
				.orElseThrow(() -> new IllegalArgumentException("课表项不存在"));
		assertOfferingAccess(scope, entry.getOfferingId());
		assertClassroomAccess(scope, entry.getClassroomId());
	}

	public boolean canAccessOffering(EducationDataScope scope, CourseOffering offering) {
		return (offering.getCampusId() != null
				&& scope.campusIds().contains(offering.getCampusId()))
				|| (offering.getTeacherId() != null
				&& scope.teacherIds().contains(offering.getTeacherId()))
				|| members != null && scope.studentIds().stream()
				.anyMatch(studentId -> members.findByOfferingIdAndStudentId(
						offering.getId(), studentId)
						.map(member -> "ACTIVE".equals(member.getEnrollmentStatus())
								|| "ENROLLED".equals(member.getEnrollmentStatus()))
						.orElse(false));
	}

	/** 管理端课表查询只能使用调用者确实拥有的数据维度。 */
	public void assertScheduleDimensionAccess(
			EducationDataScope scope,
			String dimension,
			String targetId) {
		if (scope.fullAccess()) {
			return;
		}
		String normalized = dimension == null || dimension.isBlank()
				? "ALL"
				: dimension.trim().toUpperCase();
		switch (normalized) {
			case "TEACHER" -> assertTeacherAccess(scope, targetId);
			case "STUDENT" -> assertStudentAccess(scope, targetId);
			case "ADMIN_CLASS" -> assertClassAccess(scope, targetId);
			case "TEACHING_CLASS" -> assertOfferingAccess(scope, targetId);
			case "CLASSROOM" -> assertClassroomAccess(scope, targetId);
			default -> throw new AccessDeniedException("无权按该维度查询全校课表");
		}
	}
}
