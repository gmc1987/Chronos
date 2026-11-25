package com.chronos.education.homeschool.service;

import com.chronos.Idao.IOrganizationRepository;
import com.chronos.commons.model.PageView;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.ParentProfileRepository;
import com.chronos.education.scheduling.dao.StudentGuardianRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.ParentProfile;
import com.chronos.education.scheduling.model.StudentGuardianRelation;
import com.chronos.education.scheduling.service.AcademicDataService;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.SchoolRosterSetupService;
import com.chronos.education.scheduling.service.SchoolAssessmentIdentityService;
import com.chronos.service.iService.IAuditLogService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FamilyRosterService {
	private final IOrganizationRepository organizations;
	private final AdministrativeClassRepository classes;
	private final StudentProfileRepository students;
	private final ParentProfileRepository parents;
	private final StudentGuardianRepository guardians;
	private final EducationUserBindingRepository bindings;
	private final EducationDataScopeService scopes;
	private final AcademicDataService academics;
	private final SchoolRosterSetupService roster;
	private final SchoolAssessmentIdentityService identities;
	private final TeacherAcademicProfileRepository teachers;
	private final IAuditLogService audit;

	public record ClassOption(String id, String classCode, String className, Integer gradeYear, String headTeacherName) {}
	public record HeadTeacherInput(String schoolId, String classId, String username, String teacherCode, String newTeacherName) {}
	public record StudentOption(String id, String studentNo, String studentName, String administrativeClassId) {}
	public record ParentInput(String schoolId, String parentNo, String parentName, String phone,
			String gender, String employment, String status) {}
	public record GuardianInput(String parentId, String studentId, String relationship,
			boolean primaryGuardian, boolean emergencyContact, String studentConsentStatus) {}

	@Transactional
	public SchoolRosterSetupService.RosterResult importRoster(
			String schoolId, List<SchoolRosterSetupService.RosterRow> rows, String actor) {
		return roster.importRoster(school(schoolId, actor).getId(), rows);
	}

	@Transactional(readOnly = true)
	public List<ClassOption> classOptions(String schoolId, String actor) {
		var scope = scopes.resolve(actor);
		if (schoolId != null && !schoolId.isBlank()) school(schoolId, actor);
		var allowed = schoolId == null || schoolId.isBlank()
				? scope.campusIds() : scopes.scheduleOrganizationCampuses(scope, schoolId);
		var candidates = allowed == null || (scope.fullAccess() && (schoolId == null || schoolId.isBlank()))
				? classes.findAllByOrderByGradeYearDescClassCodeAsc()
				: allowed.isEmpty() ? List.<AdministrativeClass>of() : classes.findByCampusIdIn(allowed.stream().toList());
		return candidates.stream()
				.filter(value -> "ACTIVE".equals(value.getStatus()))
				.map(value -> new ClassOption(value.getId(), value.getClassCode(), value.getClassName(),
						value.getGradeYear(), value.getHeadTeacherName()))
				.toList();
	}

	@Transactional
	public void assignHeadTeacher(HeadTeacherInput input, String actor) {
		String schoolId = school(input.schoolId(), actor).getId();
		var scopedCampuses = scopes.scheduleOrganizationCampuses(scopes.resolve(actor), schoolId);
		var target = classes.findById(input.classId())
				.filter(value -> scopedCampuses.contains(value.getCampusId()) && "ACTIVE".equals(value.getStatus()))
				.orElseThrow(() -> new AccessDeniedException("请选择本校在用行政班"));
		var teacher = input.newTeacherName() == null || input.newTeacherName().isBlank()
				? teachers.findByTeacherNo(text(input.teacherCode(), "教师编号", 64))
						.filter(value -> scopedCampuses.contains(value.getDepartmentId()))
						.orElseThrow(() -> new IllegalArgumentException("本校教师编号不存在"))
				: identities.registerTeacher(schoolId, input.teacherCode(), input.newTeacherName(), actor);
		identities.linkAccount(schoolId,
				new SchoolAssessmentIdentityService.AccountLink("TEACHER", teacher.getTeacherNo(), input.username()), actor);
		target.setHeadTeacherId(teacher.getId());
		target.setHeadTeacherName(teacher.getTeacherName());
		classes.save(target);
		audit.log(actor, "EDU_HOME_HEAD_TEACHER_ASSIGN", "classId=" + target.getId() + ", teacherId=" + teacher.getId());
	}

	@Transactional(readOnly = true)
	public PageView<StudentOption> studentOptions(String schoolId, String classId, String keyword, int page, int size,
			String actor) {
		var options = classOptions(schoolId, actor);
		if (classId != null && options.stream().noneMatch(value -> classId.equals(value.id()))) {
			throw new AccessDeniedException("无权访问该班级学生");
		}
		var classIds = options.stream().map(ClassOption::id).filter(id -> classId == null || classId.equals(id)).toList();
		if (classIds.isEmpty()) return PageView.from(List.of(), page, size);
		String search = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
		return PageView.from(students.findFamilyRoster(classIds, search,
				PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 200)))
				.map(value -> new StudentOption(value.getId(), value.getStudentNo(),
						value.getStudentName(), value.getAdministrativeClassId())));
	}

	@Transactional(readOnly = true)
	public PageView<ParentProfile> parentPage(String schoolId, int page, int size, String actor) {
		var scope = scopes.resolve(actor);
		if (schoolId != null && !schoolId.isBlank()) {
			return PageView.from(parents.findBySchoolIdInOrderByParentNo(List.of(school(schoolId, actor).getId())), page, size);
		}
		var rows = scope.fullAccess() ? parents.findAllByOrderByParentNo() : scope.schoolIds().isEmpty()
				? List.<ParentProfile>of() : parents.findBySchoolIdInOrderByParentNo(scope.schoolIds());
		return PageView.from(rows.stream().filter(parent -> parent.getSchoolId() != null).toList(), page, size);
	}

	@Transactional
	public ParentProfile saveParent(String id, ParentInput input, String actor) {
		var value = id == null ? new ParentProfile() : ownedParent(id, actor);
		String schoolId = id == null ? school(input.schoolId(), actor).getId() : value.getSchoolId();
		value.setSchoolId(schoolId);
		value.setParentNo(text(input.parentNo(), "家长编号", 64));
		value.setParentName(text(input.parentName(), "家长姓名", 128));
		value.setPhone(text(input.phone(), "联系电话", 32));
		value.setGender(input.gender());
		value.setEmployment(input.employment());
		value.setStatus("INACTIVE".equals(input.status()) ? "INACTIVE" : "ACTIVE");
		var saved = academics.saveParent(id, value);
		audit.log(actor, "EDU_HOME_PARENT_PROFILE_SAVE", "parentId=" + saved.getId());
		return saved;
	}

	@Transactional
	public void deleteParent(String id, String actor) {
		ownedParent(id, actor);
		if (bindings.findByProfileTypeAndProfileId("PARENT", id).isPresent()) {
			throw new IllegalStateException("存在账号绑定记录的家长档案不能删除");
		}
		academics.deleteParent(id);
	}

	@Transactional(readOnly = true)
	public List<StudentGuardianRelation> guardians(String parentId, String actor) {
		ownedParent(parentId, actor);
		return scopes.visibleGuardians(scopes.resolve(actor), academics.guardiansByParent(parentId));
	}

	@Transactional
	public StudentGuardianRelation addGuardian(GuardianInput input, String actor) {
		var parent = ownedParent(input.parentId(), actor);
		var student = students.findById(input.studentId()).orElseThrow(() -> new IllegalArgumentException("学生不存在"));
		scopes.assertStudentInSchool(scopes.resolve(actor), parent.getSchoolId(), student);
		var value = new StudentGuardianRelation();
		value.setParentId(parent.getId());
		value.setStudentId(student.getId());
		value.setRelationship(text(input.relationship(), "监护关系", 32));
		value.setPrimaryGuardian(input.primaryGuardian());
		value.setEmergencyContact(input.emergencyContact());
		// Consent is verified separately. The relationship starts without access regardless of consent state.
		value.setStudentConsentStatus("NOT_REQUIRED".equals(input.studentConsentStatus()) ? "NOT_REQUIRED" : "PENDING");
		return academics.saveGuardian(null, value);
	}

	@Transactional
	public void deleteGuardian(String id, String actor) {
		assertGuardian(id, actor);
		academics.deleteGuardian(id);
	}

	@Transactional(readOnly = true)
	public void assertGuardian(String id, String actor) {
		var item = guardians.findById(id).orElseThrow(() -> new IllegalArgumentException("监护关系不存在"));
		ownedParent(item.getParentId(), actor);
		scopes.assertGuardianAccess(scopes.resolve(actor), id);
	}

	private ParentProfile ownedParent(String id, String actor) {
		var parent = parents.findById(id).orElseThrow(() -> new IllegalArgumentException("家长档案不存在"));
		if (parent.getSchoolId() == null) throw new AccessDeniedException("家长档案尚未确定所属学校");
		school(parent.getSchoolId(), actor);
		return parent;
	}

	private com.chronos.model.pojo.Organization school(String requested, String actor) {
		var scope = scopes.resolve(actor);
		String id = requested == null || requested.isBlank() ? scopes.requireSingleSchool(scope) : requested;
		scopes.assertSchoolAccess(scope, id);
		return organizations.findById(id).filter(value -> "SCHOOL".equals(value.getOrganizationType())
				&& Integer.valueOf(1).equals(value.getStatus())).orElseThrow(() -> new IllegalArgumentException("请选择启用的学校"));
	}

	private String text(String value, String label, int limit) {
		if (value == null || value.isBlank() || value.strip().length() > limit) {
			throw new IllegalArgumentException(label + "不能为空且最多 " + limit + " 字");
		}
		return value.strip();
	}
}
