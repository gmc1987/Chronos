package com.chronos.education.scheduling.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

import com.chronos.commons.model.PageView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.CourseCatalogRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.dao.EducationGradeRepository;
import com.chronos.education.scheduling.dao.MajorRepository;
import com.chronos.education.scheduling.dao.ParentProfileRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.StudentGuardianRepository;
import com.chronos.education.scheduling.dao.SubjectRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.dao.TeacherTeachingAssignmentRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
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
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.model.pojo.BaseEntity;
import com.chronos.model.dto.TeacherAccountProvisioning;
import com.chronos.service.iService.ITeacherAccountProvisioningService;
import com.chronos.service.impl.BusinessNumberService;
import com.chronos.Idao.IEmployeeRepository;
import com.chronos.Idao.IOrganizationRepository;

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
	private final EducationUserBindingRepository bindings;
	private final ITeacherAccountProvisioningService teacherAccounts;
	private final BusinessNumberService businessNumbers;
	private final IEmployeeRepository employees;
	private final IOrganizationRepository organizations;

	public AcademicDataService(AcademicTermRepository terms, EducationGradeRepository grades,
			SubjectRepository subjects, CourseCatalogRepository courses, MajorRepository majors,
			AdministrativeClassRepository administrativeClasses, StudentProfileRepository students,
			ParentProfileRepository parents, StudentGuardianRepository guardians,
			TeacherAcademicProfileRepository teachers, TeacherTeachingAssignmentRepository teachingAssignments,
			TeachingClassMemberRepository members, CourseOfferingRepository offerings,
			ScheduleEntryRepository scheduleEntries, EducationUserBindingRepository bindings,
			ITeacherAccountProvisioningService teacherAccounts, BusinessNumberService businessNumbers,
			IEmployeeRepository employees, IOrganizationRepository organizations) {
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
		this.bindings = bindings;
		this.teacherAccounts = teacherAccounts;
		this.businessNumbers = businessNumbers;
		this.employees = employees;
		this.organizations = organizations;
	}

	public List<AcademicTerm> terms() {
		return terms.findAllByOrderByStartDateDesc();
	}

	public List<AcademicTerm> terms(Collection<String> organizationIds) {
		return organizationIds == null ? terms() : terms.findByOrganizationIdInOrderByStartDateDesc(organizationIds);
	}

	public Page<AcademicTerm> terms(int page, int size) {
		return terms.findAllByOrderByStartDateDesc(pageable(page, size));
	}

	public Page<AcademicTerm> terms(Collection<String> organizationIds, int page, int size) {
		return organizationIds == null ? terms(page, size)
				: terms.findByOrganizationIdInOrderByStartDateDesc(organizationIds, pageable(page, size));
	}

	@Transactional
	public AcademicTerm saveTerm(String id, AcademicTerm command) {
		if (command.getStartDate() == null || command.getEndDate() == null
				|| !command.getStartDate().isBefore(command.getEndDate())) {
			throw new IllegalArgumentException("学期开始日期必须早于结束日期");
		}
		AcademicTerm existing = id == null ? null
				: terms.findById(id).orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		if (existing != null && "ARCHIVED".equals(existing.getStatus())) {
			throw new IllegalStateException("已归档学期不能直接修改");
		}
		if (existing == null) {
			String organizationId = required(command.getOrganizationId(), "所属机构");
			var organization = organizations.findById(organizationId)
					.orElseThrow(() -> new IllegalArgumentException("所属机构不存在"));
			String localCode = localTermCode(command);
			if (terms.existsByOrganizationIdAndLocalTermCode(organizationId, localCode)) {
				throw new IllegalStateException("该机构已存在学期编码 " + localCode);
			}
			command.setLocalTermCode(localCode);
			command.setTermCode(qualifiedTermCode(organization.getOrgCode(), localCode));
		} else {
			// 学期编码已经被大量教学数据引用，创建后不可修改；名称和日期仍可维护。
			command.setOrganizationId(existing.getOrganizationId());
			command.setLocalTermCode(existing.getLocalTermCode());
			command.setTermCode(existing.getTermCode());
		}
		// 归档只能走带准备度检查和审计的专用接口，禁止普通编辑绕过归档边界。
		command.setStatus("ACTIVE");
		AcademicTerm value = entity(id, command, terms);
		if (Boolean.TRUE.equals(command.getCurrentTerm())) {
			terms.findByOrganizationIdOrderByStartDateDesc(command.getOrganizationId()).stream()
					.filter(item -> !item.getId().equals(value.getId())).forEach(item -> {
						item.setCurrentTerm(false);
						terms.save(item);
					});
		}
		return terms.save(value);
	}

	private String localTermCode(AcademicTerm command) {
		String value = command.getLocalTermCode();
		if (value == null || value.isBlank())
			value = command.getTermCode();
		if (value == null || value.isBlank())
			throw new IllegalArgumentException("学期编码不能为空");
		value = value.trim();
		int separator = value.indexOf(':');
		if (separator >= 0)
			value = value.substring(separator + 1).trim();
		if (value.isBlank() || value.length() > 64)
			throw new IllegalArgumentException("校内学期编码长度必须为 1-64 个字符");
		return value;
	}

	private String qualifiedTermCode(String organizationCode, String localCode) {
		String prefix = required(organizationCode, "机构编码").trim();
		String value = prefix + ":" + localCode;
		if (value.length() > 128)
			throw new IllegalArgumentException("机构学期编码长度不能超过 128 个字符");
		return value;
	}

	public List<EducationGrade> grades() {
		return grades.findAllByOrderByEnrollmentYearDescSortOrderAsc();
	}

	public List<EducationGrade> visibleGrades(Set<String> organizations) {
		return grades().stream().filter(grade -> organizations == null || grade.getOrganizationId() == null
				|| organizations.contains(grade.getOrganizationId())).toList();
	}

	public EducationGrade gradeById(String id) {
		return grades.findById(id).orElseThrow(() -> new IllegalArgumentException("年级不存在"));
	}

	public Page<EducationGrade> grades(int page, int size) {
		return grades.findAllByOrderByEnrollmentYearDescSortOrderAsc(pageable(page, size));
	}

	@Transactional
	public EducationGrade saveGrade(String id, EducationGrade command) {
		if (command.getEnrollmentYear() == null || command.getEnrollmentYear() < 2000) {
			throw new IllegalArgumentException("入学年份不正确");
		}
		if (id != null) command.setOrganizationId(gradeById(id).getOrganizationId());
		return grades.save(entity(id, command, grades));
	}

	@Transactional
	public void deleteGrade(String id) {
		if (administrativeClasses.countByGradeId(id) > 0 || students.countByGradeId(id) > 0
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

	public List<Subject> subjects(String organizationId) {
		return subjects.findByOrganizationIdOrderBySubjectCode(organizationId);
	}

	public List<Subject> visibleSubjects(Collection<String> organizationIds) {
		return organizationIds == null ? subjects()
				: subjects.findByOrganizationIdInOrOrganizationIdIsNullOrderBySubjectCode(
						nonEmpty(new java.util.HashSet<>(organizationIds)));
	}

	public Subject subjectById(String id) {
		return subjects.findById(id).orElseThrow(() -> new IllegalArgumentException("数据不存在"));
	}

	@Transactional
	public Subject saveSubject(String id, Subject command) {
		if (id != null)
			command.setOrganizationId(subjectById(id).getOrganizationId());
		return subjects.save(entity(id, command, subjects));
	}

	@Transactional
	public void deleteSubject(String id) {
		if (courses.countBySubjectId(id) > 0 || teachingAssignments.countBySubjectId(id) > 0) {
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

	public List<CourseCatalog> courses(String organizationId) {
		return courses.findByOrganizationIdOrderByCourseCode(organizationId);
	}

	public List<CourseCatalog> visibleCourses(Collection<String> organizationIds) {
		return organizationIds == null ? courses()
				: courses.findByOrganizationIdInOrOrganizationIdIsNullOrderByCourseCode(
						nonEmpty(new java.util.HashSet<>(organizationIds)));
	}

	public CourseCatalog courseById(String id) {
		return courses.findById(id).orElseThrow(() -> new IllegalArgumentException("数据不存在"));
	}

	@Transactional
	public CourseCatalog saveCourse(String id, CourseCatalog command) {
		if (id != null) {
			var existing = courseById(id);
			command.setOrganizationId(existing.getOrganizationId());
			if (command.getMajorIds() == null) command.setMajorIds(Set.copyOf(existing.getMajorIds()));
		}
		Set<String> majorIds = command.getMajorIds() == null ? Set.of() : command.getMajorIds();
		if (majorIds.stream().anyMatch(majorId -> majorId == null || majorId.isBlank())) {
			throw new IllegalArgumentException("适用专业不能为空");
		}
		var selectedMajors = majors.findAllById(majorIds);
		if (selectedMajors.size() != majorIds.size() || selectedMajors.stream().anyMatch(major ->
				command.getOrganizationId() != null && major.getOrganizationId() != null
						&& !command.getOrganizationId().equals(major.getOrganizationId()))) {
			throw new IllegalArgumentException("适用专业不存在或不属于课程所在机构");
		}
		command.setMajorIds(new java.util.HashSet<>(majorIds));
		if (command.getSubjectId() != null && !command.getSubjectId().isBlank()) {
			var subject = subjectById(command.getSubjectId());
			if (subject.getOrganizationId() != null
					&& !subject.getOrganizationId().equals(command.getOrganizationId())) {
				throw new IllegalArgumentException("学科不属于课程所在机构");
			}
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

	public List<Major> majors(String organizationId) {
		return majors.findByOrganizationIdOrderByMajorCode(organizationId);
	}

	public List<Major> visibleMajors(Collection<String> organizationIds) {
		return organizationIds == null ? majors()
				: majors.findByOrganizationIdInOrOrganizationIdIsNullOrderByMajorCode(
						nonEmpty(new java.util.HashSet<>(organizationIds)));
	}

	public Major majorById(String id) {
		return majors.findById(id).orElseThrow(() -> new IllegalArgumentException("数据不存在"));
	}

	@Transactional
	public Major saveMajor(String id, Major command) {
		if (id != null)
			command.setOrganizationId(majorById(id).getOrganizationId());
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
		Map<String, String> names = teacherNames(
				result.getContent().stream().map(AdministrativeClass::getHeadTeacherId).toList());
		result.forEach(item -> item
				.setHeadTeacherName(item.getHeadTeacherId() == null ? null : names.get(item.getHeadTeacherId())));
		return result;
	}

	public List<AdministrativeClass> administrativeClassesInCampus(String campusId) {
		return withHeadTeacherNames(administrativeClasses.findByCampusIdOrderByGradeYearDescClassCodeAsc(campusId));
	}

	public Page<AdministrativeClass> administrativeClassesInCampus(String campusId, int page, int size) {
		return withHeadTeacherNames(
				administrativeClasses.findByCampusIdOrderByGradeYearDescClassCodeAsc(campusId, pageable(page, size)));
	}

	public List<AdministrativeClass> administrativeClasses(EducationDataScope scope) {
		return scope.fullAccess() ? administrativeClasses()
				: withHeadTeacherNames(administrativeClasses.findVisible(nonEmpty(scope.administrativeClassIds()),
						nonEmpty(scope.gradeIds()), nonEmpty(scope.campusIds())));
	}

	public Page<AdministrativeClass> administrativeClasses(EducationDataScope scope, int page, int size) {
		return scope.fullAccess() ? administrativeClasses(page, size)
				: withHeadTeacherNames(administrativeClasses.findVisible(nonEmpty(scope.administrativeClassIds()),
						nonEmpty(scope.gradeIds()), nonEmpty(scope.campusIds()), pageable(page, size)));
	}

	@Transactional
	public AdministrativeClass saveAdministrativeClass(String id, AdministrativeClass command) {
		// 普通高中和小学没有专业；只有填写了专业时才校验中职专业目录。
		if (command.getMajorId() != null && !command.getMajorId().isBlank()) {
			majors.findById(command.getMajorId()).orElseThrow(() -> new IllegalArgumentException("专业不存在"));
		}
		if (command.getGradeId() != null && !command.getGradeId().isBlank()) {
			var grade = gradeById(command.getGradeId());
			if (grade.getOrganizationId() != null && !grade.getOrganizationId().equals(command.getCampusId())) {
				throw new IllegalArgumentException("年级不属于行政班所在机构");
			}
		}
		if (command.getHeadTeacherId() != null && !command.getHeadTeacherId().isBlank()) {
			var headTeacher = teachers.findById(command.getHeadTeacherId())
					.orElseThrow(() -> new IllegalArgumentException("班主任教师不存在"));
			if (!Boolean.TRUE.equals(headTeacher.getEnabled()) || !"ACTIVE".equals(headTeacher.getEmploymentStatus())) {
				throw new IllegalArgumentException("班主任必须是在岗教师");
			}
			String campusId = command.getCampusId();
			boolean sameCampus = campusId != null && !campusId.isBlank()
					&& (campusId.equals(headTeacher.getDepartmentId())
							|| offerings.existsByTeacherIdAndCampusIdIn(headTeacher.getId(), List.of(campusId))
							|| administrativeClasses.findByHeadTeacherId(headTeacher.getId()).stream()
									.anyMatch(item -> campusId.equals(item.getCampusId())));
			if (!sameCampus) {
				throw new IllegalArgumentException("班主任必须与行政班属于同一校区，请先完成教师归属或任教关系配置");
			}
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

	public List<StudentProfile> studentsByClass(String classId) {
		return students.findByAdministrativeClassId(classId);
	}

	public List<StudentProfile> studentsByIds(List<String> ids) {
		return students.findAllById(ids);
	}

	public Page<StudentProfile> students(int page, int size) {
		return students.findAllByOrderByStudentNo(pageable(page, size));
	}

	public List<StudentProfile> students(EducationDataScope scope) {
		return scope.fullAccess() ? students()
				: students.findVisible(nonEmpty(scope.administrativeClassIds()), nonEmpty(scope.gradeIds()));
	}

	public Page<StudentProfile> students(EducationDataScope scope, int page, int size) {
		return scope.fullAccess() ? students(page, size)
				: students.findVisible(nonEmpty(scope.administrativeClassIds()), nonEmpty(scope.gradeIds()),
						pageable(page, size));
	}

	/** 课表学生选择器按数据范围和关键词在数据库分页，不加载完整学生档案列表。 */
	public Page<StudentProfile> scheduleStudentOptions(EducationDataScope scope, String keyword, int page, int size) {
		return scheduleStudentOptions(scope, keyword, page, size, null);
	}

	public Page<StudentProfile> scheduleStudentOptions(EducationDataScope scope, String keyword, int page, int size,
			java.util.Set<String> campusIds) {
		return scheduleStudentOptions(scope, keyword, page, size, campusIds, null);
	}

	public Page<StudentProfile> scheduleStudentOptions(EducationDataScope scope, String keyword, int page, int size,
			Set<String> campusIds, String excludeEnrolledOfferingId) {
		String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(java.util.Locale.ROOT);
		if (normalizedKeyword.length() > 100) {
			throw new IllegalArgumentException("学生查询关键词不能超过100个字符");
		}
		Pageable requestedPage = pageable(page, Math.min(size, 50));
		if (campusIds != null) {
			return students.searchCampusScheduleOptions(campusIds, scope.fullAccess(),
					nonEmpty(scope.administrativeClassIds()), nonEmpty(scope.gradeIds()), normalizedKeyword,
					excludeEnrolledOfferingId, requestedPage);
		}
		return scope.fullAccess() ? students.searchScheduleOptions(normalizedKeyword, requestedPage)
				: students.searchVisibleScheduleOptions(nonEmpty(scope.administrativeClassIds()),
						nonEmpty(scope.gradeIds()), normalizedKeyword, requestedPage);
	}

	@Transactional
	public StudentProfile saveStudent(String id, StudentProfile command) {
		if (id != null) {
			StudentProfile existing = students.findLockedById(id)
					.orElseThrow(() -> new IllegalArgumentException("学生不存在"));
			if (!java.util.Objects.equals(existing.getEnrollmentStatus(), command.getEnrollmentStatus())
					|| !java.util.Objects.equals(existing.getAdministrativeClassId(),
							command.getAdministrativeClassId())
					|| !java.util.Objects.equals(existing.getGradeId(), command.getGradeId())
					|| !java.util.Objects.equals(existing.getGradeYear(), command.getGradeYear())
					|| !java.util.Objects.equals(existing.getMajorId(), command.getMajorId())) {
				throw new IllegalStateException("学籍状态、入学年份、年级、专业和行政班必须通过学籍异动功能修改");
			}
			// 普通档案编辑只更新非学籍字段，防止绕过异动审批覆盖历史关系。
			if (command.getStudentNo() != null && !command.getStudentNo().isBlank()
					&& !java.util.Objects.equals(existing.getStudentNo(), command.getStudentNo())) {
				throw new IllegalStateException("学号创建后不允许在普通编辑中修改");
			}
			existing.setStudentName(command.getStudentName());
			existing.setGender(command.getGender());
			existing.setPhone(command.getPhone());
			return students.save(existing);
		}
		// 普通教育学生不需要虚构一个专业来完成建档。
		if (command.getMajorId() != null && !command.getMajorId().isBlank()) {
			majors.findById(command.getMajorId()).orElseThrow(() -> new IllegalArgumentException("专业不存在"));
		}
		if (command.getGradeId() != null && !command.getGradeId().isBlank()) {
			grades.findById(command.getGradeId()).orElseThrow(() -> new IllegalArgumentException("年级不存在"));
		}
		var targetClass = administrativeClasses.findById(command.getAdministrativeClassId())
				.orElseThrow(() -> new IllegalArgumentException("行政班不存在"));
		if (!java.util.Objects.equals(command.getGradeId(), targetClass.getGradeId())
				|| !java.util.Objects.equals(command.getMajorId(), targetClass.getMajorId())
				|| !java.util.Objects.equals(command.getGradeYear(), targetClass.getGradeYear())) {
			throw new IllegalArgumentException("学生的入学年份、年级和专业必须与行政班一致");
		}
		if (!"ACTIVE".equals(command.getEnrollmentStatus())) {
			throw new IllegalArgumentException("新建学生的初始学籍状态必须为在读");
		}
		if (command.getStudentNo() == null || command.getStudentNo().isBlank()) {
			String educationStage = command.getGradeId() == null ? ""
					: grades.findById(command.getGradeId()).map(EducationGrade::getEducationStage).orElse("");
			command.setStudentNo(businessNumbers.nextStudentNumber(targetClass.getCampusId(), command.getGradeYear(),
					educationStage));
		}
		if (command.getRegistrationStatus() == null || command.getRegistrationStatus().isBlank()) {
			command.setRegistrationStatus("NOT_SUBMITTED");
		}
		return students.save(command);
	}

	public List<TeacherAcademicProfile> teachers() {
		return withAccountStatus(teachers.findAllByOrderByTeacherNo());
	}

	public List<TeacherAcademicProfile> headTeacherCandidates(String campusId, EducationDataScope scope) {
		if (campusId == null || campusId.isBlank()) {
			return List.of();
		}
		if (!scope.fullAccess() && !scope.campusIds().contains(campusId)) {
			throw new IllegalArgumentException("无权查看该校区的班主任候选人");
		}
		var eligibleIds = new java.util.HashSet<>(offerings.findDistinctTeacherIdsByCampusIdIn(List.of(campusId)));
		administrativeClasses.findAll().stream()
				.filter(item -> campusId.equals(item.getCampusId()) && item.getHeadTeacherId() != null)
				.map(AdministrativeClass::getHeadTeacherId).forEach(eligibleIds::add);
		return withAccountStatus(teachers.findAllByOrderByTeacherNo().stream()
				.filter(item -> Boolean.TRUE.equals(item.getEnabled()) && "ACTIVE".equals(item.getEmploymentStatus())
						&& (campusId.equals(item.getDepartmentId()) || eligibleIds.contains(item.getId())))
				.toList());
	}

	public Page<TeacherAcademicProfile> teachers(int page, int size) {
		return withAccountStatus(teachers.findAllByOrderByTeacherNo(pageable(page, size)));
	}

	public List<TeacherAcademicProfile> teachers(EducationDataScope scope) {
		if (scope.fullAccess()) {
			return teachers();
		}
		var visibleTeacherIds = new java.util.HashSet<>(scope.teacherIds());
		if (!scope.campusIds().isEmpty()) {
			// School-scoped schedulers need teachers with offerings in their school,
			// even when they do not hold a personal teacher assignment.
			visibleTeacherIds.addAll(offerings.findDistinctTeacherIdsByCampusIdIn(scope.campusIds().stream().toList()));
		}
		return withAccountStatus(teachers.findByIdInOrderByTeacherNo(nonEmpty(visibleTeacherIds)));
	}

	public List<TeacherAcademicProfile> teachers(EducationDataScope scope, String organizationId) {
		var employeeIds = employees.findVisibleByOrganization(organizationId).stream().map(employee -> employee.getId())
				.collect(java.util.stream.Collectors.toSet());
		return teachers(scope).stream().filter(item -> employeeIds.contains(item.getEmployeeId())).toList();
	}

	private List<TeacherAcademicProfile> withAccountStatus(List<TeacherAcademicProfile> values) {
		values.forEach(this::setAccountStatus);
		return values;
	}

	private Page<TeacherAcademicProfile> withAccountStatus(Page<TeacherAcademicProfile> values) {
		values.forEach(this::setAccountStatus);
		return values;
	}

	private void setAccountStatus(TeacherAcademicProfile teacher) {
		TeacherAccountProvisioning account = teacherAccounts.find(teacher.getEmployeeId());
		var binding = bindings.findByProfileTypeAndProfileId("TEACHER", teacher.getId());
		teacher.setAccountUsername(account == null ? null : account.username());
		teacher.setAccountStatus(binding.map(item -> "ACTIVE".equals(item.getStatus()) ? "ACTIVE" : "INACTIVE")
				.orElse(account == null ? "UNBOUND" : "ACCOUNT_EXISTS"));
	}

	public Page<TeacherAcademicProfile> teachers(EducationDataScope scope, int page, int size) {
		return scope.fullAccess() ? teachers(page, size)
				: withAccountStatus(
						teachers.findByIdInOrderByTeacherNo(nonEmpty(scope.teacherIds()), pageable(page, size)));
	}

	@Transactional
	public TeacherAcademicProfile saveTeacher(String id, TeacherAcademicProfile command) {
		if (id == null)
			throw new IllegalStateException("请通过教师入职审批建立档案");
		var value = teachers.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("教师档案不存在"));
		if (!java.util.Objects.equals(value.getEmployeeId(), command.getEmployeeId())
				|| !java.util.Objects.equals(value.getDepartmentId(), command.getDepartmentId())
				|| !java.util.Objects.equals(value.getEnabled(), command.getEnabled())
				|| (command.getEmploymentStatus() != null
						&& !java.util.Objects.equals(value.getEmploymentStatus(), command.getEmploymentStatus()))) {
			throw new IllegalArgumentException("所属单位及任职状态必须通过教师人事流程办理");
		}
		value.setSpecialty(command.getSpecialty());
		value.setMaxWeeklyLessons(command.getMaxWeeklyLessons());
		value.setMaxDailyLessons(command.getMaxDailyLessons());
		value.setMaxConsecutiveLessons(command.getMaxConsecutiveLessons());
		return teachers.save(value);
	}

	@Transactional
	public TeacherAcademicProfile createApprovedTeacher(String employeeId, String organizationId) {
		var employee = employees.findById(employeeId).orElseThrow(() -> new IllegalArgumentException("绑定的 IAM 员工不存在"));
		if (teachers.findByEmployeeId(employeeId).isPresent()) {
			throw new IllegalStateException("该员工已有教师档案");
		}
		var value = new TeacherAcademicProfile();
		value.setEmployeeId(employeeId);
		value.setTeacherNo(employee.getEmployeeCode());
		value.setTeacherName(employee.getEmployeeName());
		value.setDepartmentId(organizationId);
		value.setEnabled(true);
		value.setEmploymentStatus("ACTIVE");
		return bindTeacherAccount(teachers.save(value).getId());
	}

	@Transactional
	public TeacherAcademicProfile bindTeacherAccount(String id) {
		TeacherAcademicProfile value = teachers.findById(id).orElseThrow(() -> new IllegalArgumentException("教师档案不存在"));
		TeacherAccountProvisioning account = teacherAccounts.provision(value.getEmployeeId(), value.getTeacherName());
		bindings.findByUsernameAndProfileType(account.username(), "TEACHER")
				.filter(existing -> !id.equals(existing.getProfileId())).ifPresent(existing -> {
					throw new IllegalArgumentException("IAM 账号已绑定其他教师");
				});
		EducationUserBinding binding = bindings.findByProfileTypeAndProfileId("TEACHER", id)
				.orElseGet(EducationUserBinding::new);
		binding.setUsername(account.username());
		binding.setProfileType("TEACHER");
		binding.setProfileId(id);
		binding.setStatus("ACTIVE");
		bindings.save(binding);
		value.setAccountUsername(account.username());
		value.setAccountStatus("ACTIVE");
		return value;
	}

	@Transactional
	public void unbindTeacherAccount(String id) {
		bindings.findByProfileTypeAndProfileId("TEACHER", id).ifPresent(binding -> {
			binding.setStatus("INACTIVE");
			bindings.save(binding);
		});
	}

	@Transactional
	public void deleteTeacher(String id) {
		if (bindings.findByProfileTypeAndProfileId("TEACHER", id)
				.filter(binding -> "ACTIVE".equals(binding.getStatus())).isPresent()) {
			throw new IllegalStateException("教师仍绑定登录账号，解除绑定后才能删除");
		}
		if (teachingAssignments.countByTeacherId(id) > 0 || offerings.existsByTeacherId(id)) {
			throw new IllegalStateException("教师已被教学任务引用，不能删除");
		}
		teachers.deleteById(id);
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
		students.findById(studentId).orElseThrow(() -> new IllegalArgumentException("学生不存在"));
		return guardians.findByStudentIdOrderByCreateTime(studentId);
	}

	public List<StudentGuardianRelation> guardiansByParent(String parentId) {
		parents.findById(parentId).orElseThrow(() -> new IllegalArgumentException("家长不存在"));
		return guardians.findByParentIdOrderByCreateTime(parentId);
	}

	@Transactional
	public StudentGuardianRelation saveGuardian(String id, StudentGuardianRelation command) {
		if (command == null || command.getStudentId() == null || command.getParentId() == null
				|| command.getRelationship() == null || command.getRelationship().isBlank()) {
			throw new IllegalArgumentException("监护关系字段不完整");
		}
		students.findById(command.getStudentId()).orElseThrow(() -> new IllegalArgumentException("学生不存在"));
		parents.findById(command.getParentId()).orElseThrow(() -> new IllegalArgumentException("家长不存在"));
		if (Boolean.TRUE.equals(command.getPrimaryGuardian())) {
			guardians.findByStudentIdOrderByCreateTime(command.getStudentId()).stream()
					.filter(existing -> !java.util.Objects.equals(existing.getId(), id))
					.filter(existing -> Boolean.TRUE.equals(existing.getPrimaryGuardian())).findAny()
					.ifPresent(existing -> {
						throw new IllegalStateException("每名学生只能有一名主监护人");
					});
		}
		StudentGuardianRelation value = entity(id, command, guardians);
		if (id == null) {
			// 新建关系必须经过独立核验，不能因为已建档就直接获得学生数据访问权。
			value.setVerificationStatus("PENDING");
			value.setVerifiedBy(null);
			value.setVerifiedAt(null);
		}
		return guardians.save(value);
	}

	@Transactional
	public StudentGuardianRelation verifyGuardian(String id, boolean approved, String note, String actor) {
		StudentGuardianRelation value = guardians.findForUpdate(id)
				.orElseThrow(() -> new IllegalArgumentException("监护关系不存在"));
		if (!"PENDING".equals(value.getVerificationStatus())) {
			throw new IllegalStateException("当前监护关系不可核验");
		}
		if (actor.equals(value.getCreateBy())) {
			throw new org.springframework.security.access.AccessDeniedException("监护关系创建人不能核验自己的关系");
		}
		if (!approved && (note == null || note.isBlank())) {
			throw new IllegalArgumentException("驳回原因不能为空");
		}
		value.setVerificationStatus(approved ? "VERIFIED" : "REJECTED");
		value.setVerificationNote(note == null || note.isBlank() ? null : note.trim());
		value.setVerifiedBy(actor);
		value.setVerifiedAt(LocalDateTime.now());
		return guardians.save(value);
	}

	@Transactional
	public StudentGuardianRelation restrictGuardian(String id, String level, String reason, java.time.LocalDate from,
			java.time.LocalDate to) {
		StudentGuardianRelation value = guardians.findForUpdate(id)
				.orElseThrow(() -> new IllegalArgumentException("监护关系不存在"));
		String normalized = level == null ? "NONE" : level.trim().toUpperCase(java.util.Locale.ROOT);
		if (!java.util.Set.of("NONE", "CONTACT_ONLY", "NO_ACCESS").contains(normalized)) {
			throw new IllegalArgumentException("监护限制级别无效");
		}
		if (!"NONE".equals(normalized) && (reason == null || reason.isBlank())) {
			throw new IllegalArgumentException("设置监护限制时必须填写原因");
		}
		if (from != null && to != null && to.isBefore(from)) {
			throw new IllegalArgumentException("监护限制结束日期不能早于开始日期");
		}
		value.setRestrictionLevel(normalized);
		value.setRestrictionReason(reason == null || reason.isBlank() ? null : reason.trim());
		value.setRestrictionFrom(from);
		value.setRestrictionTo(to);
		return guardians.save(value);
	}

	@Transactional
	public StudentGuardianRelation consentGuardian(String id, String status, LocalDateTime expiresAt) {
		StudentGuardianRelation value = guardians.findForUpdate(id)
				.orElseThrow(() -> new IllegalArgumentException("监护关系不存在"));
		String normalized = status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
		if (!java.util.Set.of("NOT_REQUIRED", "PENDING", "GRANTED", "DENIED", "REVOKED").contains(normalized)) {
			throw new IllegalArgumentException("学生授权状态无效");
		}
		value.setStudentConsentStatus(normalized);
		value.setStudentConsentedAt("GRANTED".equals(normalized) ? LocalDateTime.now() : null);
		value.setConsentExpiresAt("GRANTED".equals(normalized) ? expiresAt : null);
		return guardians.save(value);
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
		Map<String, String> names = teacherNames(
				result.getContent().stream().map(TeacherTeachingAssignment::getTeacherId).toList());
		result.forEach(item -> item.setTeacherName(names.get(item.getTeacherId())));
		return result;
	}

	public List<TeacherTeachingAssignment> teachingAssignments(EducationDataScope scope) {
		return scope.fullAccess() ? teachingAssignments()
				: withTeacherNames(teachingAssignments.findVisible(nonEmpty(scope.teacherIds()),
						nonEmpty(scope.administrativeClassIds()), nonEmpty(scope.gradeIds())));
	}

	public List<TeacherTeachingAssignment> teachingAssignments(EducationDataScope scope, String organizationId) {
		var termIds = terms.findByOrganizationIdOrderByStartDateDesc(organizationId).stream().map(AcademicTerm::getId)
				.toList();
		if (termIds.isEmpty()) {
			return List.of();
		}
		return withTeacherNames(teachingAssignments.findByAcademicTermIdInOrderByCreateTimeDesc(termIds).stream()
				.filter(item -> scope.fullAccess() || scope.teacherIds().contains(item.getTeacherId())
						|| (item.getAdministrativeClassId() != null
								&& scope.administrativeClassIds().contains(item.getAdministrativeClassId()))
						|| (item.getGradeId() != null && scope.gradeIds().contains(item.getGradeId())))
				.toList());
	}

	public Page<TeacherTeachingAssignment> teachingAssignments(EducationDataScope scope, int page, int size) {
		return scope.fullAccess() ? teachingAssignments(page, size)
				: withTeacherNames(teachingAssignments.findVisible(nonEmpty(scope.teacherIds()),
						nonEmpty(scope.administrativeClassIds()), nonEmpty(scope.gradeIds()), pageable(page, size)));
	}

	@Transactional
	public TeacherTeachingAssignment saveTeachingAssignment(String id, TeacherTeachingAssignment command) {
		var term = terms.findById(command.getAcademicTermId()).orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		teachers.findById(command.getTeacherId()).orElseThrow(() -> new IllegalArgumentException("教师不存在"));
		var subject = subjectById(command.getSubjectId());
		if (subject.getOrganizationId() != null && !subject.getOrganizationId().equals(term.getOrganizationId())) {
			throw new IllegalArgumentException("学科不属于学期所在机构");
		}
		if (id != null && command.getCourseId() == null) {
			teachingAssignments.findById(id).ifPresent(existing -> command.setCourseId(existing.getCourseId()));
		}
		if (command.getCourseId() != null) {
			var course = courseById(command.getCourseId());
			if (!Boolean.TRUE.equals(course.getEnabled()) || !subject.getId().equals(course.getSubjectId())
					|| (course.getOrganizationId() != null && !course.getOrganizationId().equals(term.getOrganizationId()))) {
				throw new IllegalArgumentException("课程须启用，并属于所选学科和学期所在机构");
			}
		}
		if (command.getGradeId() != null && !command.getGradeId().isBlank()) {
			var grade = gradeById(command.getGradeId());
			if (grade.getOrganizationId() != null && !grade.getOrganizationId().equals(term.getOrganizationId())) {
				throw new IllegalArgumentException("年级不属于学期所在机构");
			}
		}
		if (command.getAdministrativeClassId() != null && !command.getAdministrativeClassId().isBlank()) {
			var schoolClass = administrativeClasses.findById(command.getAdministrativeClassId())
					.orElseThrow(() -> new IllegalArgumentException("行政班不存在"));
			if (!java.util.Objects.equals(schoolClass.getCampusId(), term.getOrganizationId())) {
				throw new IllegalArgumentException("行政班不属于学期所在机构");
			}
			if (command.getGradeId() != null && !java.util.Objects.equals(command.getGradeId(), schoolClass.getGradeId())) {
				throw new IllegalArgumentException("所选年级与行政班不一致");
			}
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

	public record TeachingClassMemberPage(PageView<TeachingClassMember> page, long enrolledCount) {}

	@Transactional(readOnly = true)
	public TeachingClassMemberPage members(String offeringId, EducationDataScope scope, int page, int size) {
		if (!offerings.existsById(offeringId)) throw new IllegalArgumentException("教学任务不存在");
		var studentIds = nonEmpty(scope.studentIds());
		var classIds = nonEmpty(scope.administrativeClassIds());
		var gradeIds = nonEmpty(scope.gradeIds());
		var result = members.findVisiblePage(offeringId, scope.fullAccess(), studentIds, classIds, gradeIds,
				pageable(page, size));
		long enrolledCount = members.countVisibleEnrolled(offeringId, scope.fullAccess(), studentIds, classIds, gradeIds);
		return new TeachingClassMemberPage(PageView.from(result), enrolledCount);
	}

	@Transactional
	public TeachingClassMember enroll(String offeringId, String studentId) {
		return enrollInternal(offeringId, studentId, false);
	}

	/** 合班同步只调用这一入口，保留普通学生选课的冲突检查。 */
	@Transactional
	public TeachingClassMember enrollFromCombined(String offeringId, String studentId) {
		return enrollInternal(offeringId, studentId, true);
	}

	/** 复学只恢复原有成员关系，并沿用原来源标记和正常选课冲突检查。 */
	@Transactional
	public TeachingClassMember restoreAfterStatusChange(String offeringId, String studentId) {
		TeachingClassMember previous = members.findByOfferingIdAndStudentId(offeringId, studentId)
				.orElseThrow(() -> new IllegalStateException("原教学班成员关系已不存在"));
		String enrollmentSource = previous.getEnrollmentSource();
		boolean combined = "COMBINED".equals(offerings.findById(offeringId)
				.orElseThrow(() -> new IllegalStateException("原教学任务已不存在")).getOfferingMode());
		TeachingClassMember restored = enrollInternal(offeringId, studentId, combined);
		restored.setEnrollmentSource(enrollmentSource);
		return members.save(restored);
	}

	/** 校内转班时将学生加入目标行政班关联的普通教学任务。 */
	@Transactional
	public TeachingClassMember enrollFromAdministrativeClass(String offeringId, String studentId) {
		CourseOffering offering = offerings.findById(offeringId)
				.orElseThrow(() -> new IllegalArgumentException("目标教学任务不存在"));
		if (!"NORMAL".equals(offering.getOfferingMode())) {
			throw new IllegalStateException("合班任务须由来源行政班同步成员");
		}
		TeachingClassMember member = enrollInternal(offeringId, studentId, false);
		member.setEnrollmentSource("SOURCE_CLASS");
		return members.save(member);
	}

	private TeachingClassMember enrollInternal(String offeringId, String studentId, boolean fromCombined) {
		var offering = offerings.findById(offeringId).orElseThrow(() -> new IllegalArgumentException("教学任务不存在"));
		if ("COMBINED".equals(offering.getOfferingMode()) != fromCombined) {
			throw new IllegalStateException("合班课成员只能通过来源行政班同步维护");
		}
		students.findById(studentId).orElseThrow(() -> new IllegalArgumentException("学生不存在"));
		TeachingClassMember member = members.findByOfferingIdAndStudentId(offeringId, studentId)
				.orElseGet(TeachingClassMember::new);
		if (member.getId() != null && "ENROLLED".equals(member.getEnrollmentStatus())) {
			return member;
		}
		assertStudentScheduleAvailable(studentId, offeringId);
		long enrolled = members.findByOfferingIdOrderByCreateTime(offeringId).stream()
				.filter(item -> "ENROLLED".equals(item.getEnrollmentStatus())).count();
		if (enrolled >= offering.getStudentCount()) {
			throw new IllegalStateException("教学班人数已达到容量上限");
		}
		member.setOfferingId(offeringId);
		member.setStudentId(studentId);
		member.setEnrollmentStatus("ENROLLED");
		member.setEnrollmentSource(fromCombined ? "SOURCE_CLASS" : "MANUAL");
		member.setEnrolledAt(LocalDateTime.now());
		member.setWithdrawnAt(null);
		return members.save(member);
	}

	@Transactional
	public TeachingClassMember withdraw(String offeringId, String studentId) {
		if ("COMBINED".equals(offerings.findById(offeringId).orElseThrow(() -> new IllegalArgumentException("教学任务不存在"))
				.getOfferingMode())) {
			throw new IllegalStateException("合班课成员只能通过来源行政班同步维护");
		}
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
				boolean conflicts = target.stream()
						.anyMatch(candidate -> candidate.getSemesterCode().equals(existing.getSemesterCode())
								&& candidate.getDayOfWeek().equals(existing.getDayOfWeek())
								&& candidate.getPeriodNo() <= existing.getPeriodNo() + existing.getDurationPeriods() - 1
								&& existing.getPeriodNo() <= candidate.getPeriodNo() + candidate.getDurationPeriods()
										- 1
								&& ("ALL".equals(candidate.getWeekPattern()) || "ALL".equals(existing.getWeekPattern())
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

	private String required(String value, String label) {
		if (value == null || value.isBlank())
			throw new IllegalArgumentException(label + "不能为空");
		return value;
	}

	private List<AdministrativeClass> withHeadTeacherNames(List<AdministrativeClass> rows) {
		Map<String, String> names = teacherNames(rows.stream().map(AdministrativeClass::getHeadTeacherId).toList());
		rows.forEach(item -> item
				.setHeadTeacherName(item.getHeadTeacherId() == null ? null : names.get(item.getHeadTeacherId())));
		return rows;
	}

	private Page<AdministrativeClass> withHeadTeacherNames(Page<AdministrativeClass> rows) {
		Map<String, String> names = teacherNames(
				rows.getContent().stream().map(AdministrativeClass::getHeadTeacherId).toList());
		rows.forEach(item -> item
				.setHeadTeacherName(item.getHeadTeacherId() == null ? null : names.get(item.getHeadTeacherId())));
		return rows;
	}

	private List<TeacherTeachingAssignment> withTeacherNames(List<TeacherTeachingAssignment> rows) {
		Map<String, String> names = teacherNames(rows.stream().map(TeacherTeachingAssignment::getTeacherId).toList());
		rows.forEach(item -> item.setTeacherName(names.get(item.getTeacherId())));
		return rows;
	}

	private Page<TeacherTeachingAssignment> withTeacherNames(Page<TeacherTeachingAssignment> rows) {
		Map<String, String> names = teacherNames(
				rows.getContent().stream().map(TeacherTeachingAssignment::getTeacherId).toList());
		rows.forEach(item -> item.setTeacherName(names.get(item.getTeacherId())));
		return rows;
	}

	private Map<String, String> teacherNames(Collection<String> ids) {
		List<String> validIds = ids.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
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

	private <T> T entity(String id, T command,
			org.springframework.data.jpa.repository.JpaRepository<T, String> repository) {
		if (id == null) {
			return command;
		}
		T existing = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("数据不存在"));
		BaseEntity target = (BaseEntity) command;
		BaseEntity source = (BaseEntity) existing;
		target.setId(id);
		// 更新 DTO 直接复用实体时保留不可变的创建审计字段。
		target.setCreateBy(source.getCreateBy());
		target.setCreateTime(source.getCreateTime());
		return command;
	}
}
