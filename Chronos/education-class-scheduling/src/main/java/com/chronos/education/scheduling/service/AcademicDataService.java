package com.chronos.education.scheduling.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.CourseCatalogRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.EducationGradeRepository;
import com.chronos.education.scheduling.dao.MajorRepository;
import com.chronos.education.scheduling.dao.ParentProfileRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.StudentGuardianRepository;
import com.chronos.education.scheduling.dao.SubjectRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.dao.TeacherTeachingAssignmentRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.CourseCatalog;
import com.chronos.education.scheduling.model.EducationGrade;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.Major;
import com.chronos.education.scheduling.model.ParentProfile;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.StudentGuardianRelation;
import com.chronos.education.scheduling.model.Subject;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.chronos.education.scheduling.model.TeacherTeachingAssignment;
import com.chronos.education.scheduling.model.TeachingClassMember;
import com.chronos.model.pojo.BaseEntity;

/** 教务主数据与走班成员服务，删除操作均先校验业务引用。 */
@Service
public class AcademicDataService {
	private final AcademicTermRepository terms;
	private final EducationGradeRepository grades;
	private final SubjectRepository subjects;
	private final CourseCatalogRepository courses;
	private final MajorRepository majors;
	private final AdministrativeClassRepository administrativeClasses;
	private final StudentProfileRepository students;
	private final ParentProfileRepository parents;
	private final StudentGuardianRepository guardians;
	private final TeacherAcademicProfileRepository teachers;
	private final TeacherTeachingAssignmentRepository teachingAssignments;
	private final TeachingClassMemberRepository members;
	private final CourseOfferingRepository offerings;
	private final ScheduleEntryRepository scheduleEntries;

	public AcademicDataService(
			AcademicTermRepository terms,
			EducationGradeRepository grades,
			SubjectRepository subjects,
			CourseCatalogRepository courses,
			MajorRepository majors,
			AdministrativeClassRepository administrativeClasses,
			StudentProfileRepository students,
			ParentProfileRepository parents,
			StudentGuardianRepository guardians,
			TeacherAcademicProfileRepository teachers,
			TeacherTeachingAssignmentRepository teachingAssignments,
			TeachingClassMemberRepository members,
			CourseOfferingRepository offerings,
			ScheduleEntryRepository scheduleEntries) {
		this.terms = terms;
		this.grades = grades;
		this.subjects = subjects;
		this.courses = courses;
		this.majors = majors;
		this.administrativeClasses = administrativeClasses;
		this.students = students;
		this.parents = parents;
		this.guardians = guardians;
		this.teachers = teachers;
		this.teachingAssignments = teachingAssignments;
		this.members = members;
		this.offerings = offerings;
		this.scheduleEntries = scheduleEntries;
	}

	public List<AcademicTerm> terms() {
		return terms.findAllByOrderByStartDateDesc();
	}

	public Page<AcademicTerm> terms(int page, int size) {
		return terms.findAllByOrderByStartDateDesc(pageable(page, size));
	}

	@Transactional
	public AcademicTerm saveTerm(String id, AcademicTerm command) {
		if (command.getStartDate() == null || command.getEndDate() == null
				|| !command.getStartDate().isBefore(command.getEndDate())) {
			throw new IllegalArgumentException("学期开始日期必须早于结束日期");
		}
		AcademicTerm value = entity(id, command, terms);
		if (Boolean.TRUE.equals(command.getCurrentTerm())) {
			terms.findAll().stream().filter(item -> !item.getId().equals(value.getId())).forEach(item -> {
				item.setCurrentTerm(false);
				terms.save(item);
			});
		}
		return terms.save(value);
	}

	public List<EducationGrade> grades() {
		return grades.findAllByOrderByEnrollmentYearDescSortOrderAsc();
	}

	public Page<EducationGrade> grades(int page, int size) {
		return grades.findAllByOrderByEnrollmentYearDescSortOrderAsc(pageable(page, size));
	}

	@Transactional
	public EducationGrade saveGrade(String id, EducationGrade command) {
		if (command.getEnrollmentYear() == null || command.getEnrollmentYear() < 2000) {
			throw new IllegalArgumentException("入学年份不正确");
		}
		return grades.save(entity(id, command, grades));
	}

	@Transactional
	public void deleteGrade(String id) {
		if (administrativeClasses.countByGradeId(id) > 0
				|| students.countByGradeId(id) > 0
				|| teachingAssignments.countByGradeId(id) > 0) {
			throw new IllegalStateException("年级已被班级、学生或任教关系引用，不能删除");
		}
		grades.deleteById(id);
	}

	public List<Subject> subjects() {
		return subjects.findAllByOrderBySortOrderAscSubjectCodeAsc();
	}

	public Page<Subject> subjects(int page, int size) {
		return subjects.findAllByOrderBySortOrderAscSubjectCodeAsc(pageable(page, size));
	}

	@Transactional
	public Subject saveSubject(String id, Subject command) {
		return subjects.save(entity(id, command, subjects));
	}

	@Transactional
	public void deleteSubject(String id) {
		if (courses.countBySubjectId(id) > 0
				|| teachingAssignments.countBySubjectId(id) > 0) {
			throw new IllegalStateException("学科已被课程或任教关系引用，不能删除");
		}
		subjects.deleteById(id);
	}

	public List<CourseCatalog> courses() {
		return courses.findAllByOrderByCourseCode();
	}

	public Page<CourseCatalog> courses(int page, int size) {
		return courses.findAllByOrderByCourseCode(pageable(page, size));
	}

	@Transactional
	public CourseCatalog saveCourse(String id, CourseCatalog command) {
		if (command.getSubjectId() != null && !command.getSubjectId().isBlank()) {
			subjects.findById(command.getSubjectId())
					.orElseThrow(() -> new IllegalArgumentException("学科不存在"));
		}
		if (command.getTotalHours() == null || command.getTotalHours() <= 0) {
			throw new IllegalArgumentException("课程总学时必须大于 0");
		}
		int detailHours = value(command.getTheoryHours()) + value(command.getPracticeHours());
		if (detailHours != command.getTotalHours()) {
			throw new IllegalArgumentException("理论学时与实践学时之和必须等于总学时");
		}
		return courses.save(entity(id, command, courses));
	}

	public List<Major> majors() {
		return majors.findAllByOrderByMajorCode();
	}

	public Page<Major> majors(int page, int size) {
		return majors.findAllByOrderByMajorCode(pageable(page, size));
	}

	@Transactional
	public Major saveMajor(String id, Major command) {
		return majors.save(entity(id, command, majors));
	}

	@Transactional
	public void deleteMajor(String id) {
		if (administrativeClasses.countByMajorId(id) > 0 || students.countByMajorId(id) > 0) {
			throw new IllegalStateException("专业已被班级或学生引用，不能删除");
		}
		majors.deleteById(id);
	}

	public List<AdministrativeClass> administrativeClasses() {
		return withHeadTeacherNames(administrativeClasses.findAllByOrderByGradeYearDescClassCodeAsc());
	}

	public Page<AdministrativeClass> administrativeClasses(int page, int size) {
		Page<AdministrativeClass> result = administrativeClasses
				.findAllByOrderByGradeYearDescClassCodeAsc(pageable(page, size));
		Map<String, String> names = teacherNames(result.getContent().stream()
				.map(AdministrativeClass::getHeadTeacherId)
				.toList());
		result.forEach(item -> item.setHeadTeacherName(names.get(item.getHeadTeacherId())));
		return result;
	}

	public List<AdministrativeClass> administrativeClasses(EducationDataScope scope) {
		return scope.fullAccess()
				? administrativeClasses()
				: withHeadTeacherNames(administrativeClasses.findVisible(
						nonEmpty(scope.administrativeClassIds()),
						nonEmpty(scope.gradeIds()),
						nonEmpty(scope.campusIds())));
	}

	public Page<AdministrativeClass> administrativeClasses(
			EducationDataScope scope,
			int page,
			int size) {
		return scope.fullAccess()
				? administrativeClasses(page, size)
				: withHeadTeacherNames(administrativeClasses.findVisible(
						nonEmpty(scope.administrativeClassIds()),
						nonEmpty(scope.gradeIds()),
						nonEmpty(scope.campusIds()),
						pageable(page, size)));
	}

	@Transactional
	public AdministrativeClass saveAdministrativeClass(String id, AdministrativeClass command) {
		majors.findById(command.getMajorId()).orElseThrow(() -> new IllegalArgumentException("专业不存在"));
		if (command.getGradeId() != null && !command.getGradeId().isBlank()) {
			grades.findById(command.getGradeId())
					.orElseThrow(() -> new IllegalArgumentException("年级不存在"));
		}
		return administrativeClasses.save(entity(id, command, administrativeClasses));
	}

	@Transactional
	public void deleteAdministrativeClass(String id) {
		if (students.countByAdministrativeClassId(id) > 0) {
			throw new IllegalStateException("行政班已有学生，不能删除");
		}
		administrativeClasses.deleteById(id);
	}

	public List<StudentProfile> students() {
		return students.findAllByOrderByStudentNo();
	}

	public Page<StudentProfile> students(int page, int size) {
		return students.findAllByOrderByStudentNo(pageable(page, size));
	}

	public List<StudentProfile> students(EducationDataScope scope) {
		return scope.fullAccess()
				? students()
				: students.findVisible(
						nonEmpty(scope.administrativeClassIds()),
						nonEmpty(scope.gradeIds()));
	}

	public Page<StudentProfile> students(
			EducationDataScope scope,
			int page,
			int size) {
		return scope.fullAccess()
				? students(page, size)
				: students.findVisible(
						nonEmpty(scope.administrativeClassIds()),
						nonEmpty(scope.gradeIds()),
						pageable(page, size));
	}

	@Transactional
	public StudentProfile saveStudent(String id, StudentProfile command) {
		majors.findById(command.getMajorId()).orElseThrow(() -> new IllegalArgumentException("专业不存在"));
		if (command.getGradeId() != null && !command.getGradeId().isBlank()) {
			grades.findById(command.getGradeId())
					.orElseThrow(() -> new IllegalArgumentException("年级不存在"));
		}
		administrativeClasses.findById(command.getAdministrativeClassId())
				.orElseThrow(() -> new IllegalArgumentException("行政班不存在"));
		return students.save(entity(id, command, students));
	}

	public List<TeacherAcademicProfile> teachers() {
		return teachers.findAllByOrderByTeacherNo();
	}

	public Page<TeacherAcademicProfile> teachers(int page, int size) {
		return teachers.findAllByOrderByTeacherNo(pageable(page, size));
	}

	public List<TeacherAcademicProfile> teachers(EducationDataScope scope) {
		return scope.fullAccess()
				? teachers()
				: teachers.findByIdInOrderByTeacherNo(nonEmpty(scope.teacherIds()));
	}

	public Page<TeacherAcademicProfile> teachers(
			EducationDataScope scope,
			int page,
			int size) {
		return scope.fullAccess()
				? teachers(page, size)
				: teachers.findByIdInOrderByTeacherNo(
						nonEmpty(scope.teacherIds()),
						pageable(page, size));
	}

	@Transactional
	public TeacherAcademicProfile saveTeacher(String id, TeacherAcademicProfile command) {
		return teachers.save(entity(id, command, teachers));
	}

	public List<ParentProfile> parents() {
		return parents.findAllByOrderByParentNo();
	}

	public Page<ParentProfile> parents(int page, int size) {
		return parents.findAllByOrderByParentNo(pageable(page, size));
	}

	@Transactional
	public ParentProfile saveParent(String id, ParentProfile command) {
		if (command.getPhone() == null || command.getPhone().isBlank()) {
			throw new IllegalArgumentException("家长联系电话不能为空");
		}
		return parents.save(entity(id, command, parents));
	}

	@Transactional
	public void deleteParent(String id) {
		if (guardians.countByParentId(id) > 0) {
			throw new IllegalStateException("家长已关联学生，不能删除");
		}
		parents.deleteById(id);
	}

	public List<StudentGuardianRelation> guardians(String studentId) {
		students.findById(studentId)
				.orElseThrow(() -> new IllegalArgumentException("学生不存在"));
		return guardians.findByStudentIdOrderByCreateTime(studentId);
	}

	public List<StudentGuardianRelation> guardiansByParent(String parentId) {
		parents.findById(parentId)
				.orElseThrow(() -> new IllegalArgumentException("家长不存在"));
		return guardians.findByParentIdOrderByCreateTime(parentId);
	}

	@Transactional
	public StudentGuardianRelation saveGuardian(
			String id,
			StudentGuardianRelation command) {
		students.findById(command.getStudentId())
				.orElseThrow(() -> new IllegalArgumentException("学生不存在"));
		parents.findById(command.getParentId())
				.orElseThrow(() -> new IllegalArgumentException("家长不存在"));
		return guardians.save(entity(id, command, guardians));
	}

	@Transactional
	public void deleteGuardian(String id) {
		guardians.deleteById(id);
	}

	public List<TeacherTeachingAssignment> teachingAssignments() {
		return withTeacherNames(teachingAssignments.findAllByOrderByCreateTimeDesc());
	}

	public Page<TeacherTeachingAssignment> teachingAssignments(int page, int size) {
		Page<TeacherTeachingAssignment> result = teachingAssignments
				.findAllByOrderByCreateTimeDesc(pageable(page, size));
		Map<String, String> names = teacherNames(result.getContent().stream()
				.map(TeacherTeachingAssignment::getTeacherId)
				.toList());
		result.forEach(item -> item.setTeacherName(names.get(item.getTeacherId())));
		return result;
	}

	public List<TeacherTeachingAssignment> teachingAssignments(EducationDataScope scope) {
		return scope.fullAccess()
				? teachingAssignments()
				: withTeacherNames(teachingAssignments.findVisible(
						nonEmpty(scope.teacherIds()),
						nonEmpty(scope.administrativeClassIds()),
						nonEmpty(scope.gradeIds())));
	}

	public Page<TeacherTeachingAssignment> teachingAssignments(
			EducationDataScope scope,
			int page,
			int size) {
		return scope.fullAccess()
				? teachingAssignments(page, size)
				: withTeacherNames(teachingAssignments.findVisible(
						nonEmpty(scope.teacherIds()),
						nonEmpty(scope.administrativeClassIds()),
						nonEmpty(scope.gradeIds()),
						pageable(page, size)));
	}

	@Transactional
	public TeacherTeachingAssignment saveTeachingAssignment(
			String id,
			TeacherTeachingAssignment command) {
		terms.findById(command.getAcademicTermId())
				.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		teachers.findById(command.getTeacherId())
				.orElseThrow(() -> new IllegalArgumentException("教师不存在"));
		subjects.findById(command.getSubjectId())
				.orElseThrow(() -> new IllegalArgumentException("学科不存在"));
		if (command.getGradeId() != null && !command.getGradeId().isBlank()) {
			grades.findById(command.getGradeId())
					.orElseThrow(() -> new IllegalArgumentException("年级不存在"));
		}
		if (command.getAdministrativeClassId() != null
				&& !command.getAdministrativeClassId().isBlank()) {
			administrativeClasses.findById(command.getAdministrativeClassId())
					.orElseThrow(() -> new IllegalArgumentException("行政班不存在"));
		}
		return teachingAssignments.save(entity(id, command, teachingAssignments));
	}

	@Transactional
	public void deleteTeachingAssignment(String id) {
		teachingAssignments.deleteById(id);
	}

	public List<TeachingClassMember> members(String offeringId) {
		offerings.findById(offeringId).orElseThrow(() -> new IllegalArgumentException("教学任务不存在"));
		return members.findByOfferingIdOrderByCreateTime(offeringId);
	}

	@Transactional
	public TeachingClassMember enroll(String offeringId, String studentId) {
		var offering = offerings.findById(offeringId)
				.orElseThrow(() -> new IllegalArgumentException("教学任务不存在"));
		students.findById(studentId).orElseThrow(() -> new IllegalArgumentException("学生不存在"));
		TeachingClassMember member = members.findByOfferingIdAndStudentId(offeringId, studentId)
				.orElseGet(TeachingClassMember::new);
		if ("ENROLLED".equals(member.getEnrollmentStatus())) {
			return member;
		}
		assertStudentScheduleAvailable(studentId, offeringId);
		long enrolled = members.findByOfferingIdOrderByCreateTime(offeringId).stream()
				.filter(item -> "ENROLLED".equals(item.getEnrollmentStatus()))
				.count();
		if (enrolled >= offering.getStudentCount()) {
			throw new IllegalStateException("教学班人数已达到容量上限");
		}
		member.setOfferingId(offeringId);
		member.setStudentId(studentId);
		member.setEnrollmentStatus("ENROLLED");
		member.setEnrolledAt(LocalDateTime.now());
		member.setWithdrawnAt(null);
		return members.save(member);
	}

	@Transactional
	public TeachingClassMember withdraw(String offeringId, String studentId) {
		TeachingClassMember member = members.findByOfferingIdAndStudentId(offeringId, studentId)
				.orElseThrow(() -> new IllegalArgumentException("学生不在该教学班"));
		member.setEnrollmentStatus("WITHDRAWN");
		member.setWithdrawnAt(LocalDateTime.now());
		return members.save(member);
	}

	private void assertStudentScheduleAvailable(String studentId, String targetOfferingId) {
		List<ScheduleEntry> target = scheduleEntries.findByOfferingId(targetOfferingId);
		for (TeachingClassMember membership : members.findByStudentIdAndEnrollmentStatus(studentId, "ENROLLED")) {
			for (ScheduleEntry existing : scheduleEntries.findByOfferingId(membership.getOfferingId())) {
				boolean conflicts = target.stream().anyMatch(candidate ->
						candidate.getSemesterCode().equals(existing.getSemesterCode())
								&& candidate.getDayOfWeek().equals(existing.getDayOfWeek())
								&& candidate.getPeriodNo() <= existing.getPeriodNo() + existing.getDurationPeriods() - 1
								&& existing.getPeriodNo() <= candidate.getPeriodNo() + candidate.getDurationPeriods() - 1
								&& ("ALL".equals(candidate.getWeekPattern())
										|| "ALL".equals(existing.getWeekPattern())
										|| candidate.getWeekPattern().equals(existing.getWeekPattern()))
								&& candidate.getStartWeek() <= existing.getEndWeek()
								&& candidate.getEndWeek() >= existing.getStartWeek());
				if (conflicts) {
					throw new IllegalStateException("选课冲突：学生在该时间段已有课程");
				}
			}
		}
	}

	private int value(Integer number) {
		return number == null ? 0 : number;
	}

	private List<AdministrativeClass> withHeadTeacherNames(List<AdministrativeClass> rows) {
		Map<String, String> names = teacherNames(rows.stream()
				.map(AdministrativeClass::getHeadTeacherId)
				.toList());
		rows.forEach(item -> item.setHeadTeacherName(names.get(item.getHeadTeacherId())));
		return rows;
	}

	private Page<AdministrativeClass> withHeadTeacherNames(Page<AdministrativeClass> rows) {
		Map<String, String> names = teacherNames(rows.getContent().stream()
				.map(AdministrativeClass::getHeadTeacherId)
				.toList());
		rows.forEach(item -> item.setHeadTeacherName(names.get(item.getHeadTeacherId())));
		return rows;
	}

	private List<TeacherTeachingAssignment> withTeacherNames(List<TeacherTeachingAssignment> rows) {
		Map<String, String> names = teacherNames(rows.stream()
				.map(TeacherTeachingAssignment::getTeacherId)
				.toList());
		rows.forEach(item -> item.setTeacherName(names.get(item.getTeacherId())));
		return rows;
	}

	private Page<TeacherTeachingAssignment> withTeacherNames(Page<TeacherTeachingAssignment> rows) {
		Map<String, String> names = teacherNames(rows.getContent().stream()
				.map(TeacherTeachingAssignment::getTeacherId)
				.toList());
		rows.forEach(item -> item.setTeacherName(names.get(item.getTeacherId())));
		return rows;
	}

	private Map<String, String> teacherNames(Collection<String> ids) {
		List<String> validIds = ids.stream()
				.filter(id -> id != null && !id.isBlank())
				.distinct()
				.toList();
		if (validIds.isEmpty()) {
			return Map.of();
		}
		// 业务关系历史上同时使用过教师档案 ID 和 IAM employeeId，批量按两种键查询，
		// 避免列表渲染时逐行查询，也兼容旧数据。
		Map<String, String> result = new HashMap<>();
		teachers.findByIdInOrEmployeeIdIn(validIds).forEach(profile -> {
			String name = profile.getTeacherName();
			if (name == null || name.isBlank()) {
				return;
			}
			if (profile.getId() != null) {
				result.put(profile.getId(), name);
			}
			if (profile.getEmployeeId() != null) {
				result.put(profile.getEmployeeId(), name);
			}
		});
		return result;
	}

	private Pageable pageable(int page, int size) {
		return PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
	}

	/** Hibernate/PostgreSQL 对空 IN 参数的处理依版本不同，使用不可命中的占位值保持查询稳定。 */
	private List<String> nonEmpty(java.util.Set<String> values) {
		return values.isEmpty() ? List.of("__NO_EDUCATION_RESOURCE__") : values.stream().toList();
	}

	private <T> T entity(String id, T command, org.springframework.data.jpa.repository.JpaRepository<T, String> repository) {
		if (id == null) {
			return command;
		}
		T existing = repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("数据不存在"));
		BaseEntity target = (BaseEntity) command;
		BaseEntity source = (BaseEntity) existing;
		target.setId(id);
		// 更新 DTO 直接复用实体时保留不可变的创建审计字段。
		target.setCreateBy(source.getCreateBy());
		target.setCreateTime(source.getCreateTime());
		return command;
	}
}
