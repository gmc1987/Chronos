package com.chronos.education.scheduling.service;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.model.pojo.Employee;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.chronos.Idao.IOrganizationRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.service.iService.IAuditLogService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SchoolAssessmentIdentityService {
	private final IOrganizationRepository organizations;
	private final EducationDataScopeService scopes;
	private final IEmployeeRepository employees;
	private final TeacherAcademicProfileRepository teachers;
	private final StudentProfileRepository students;
	private final IAdminUserRepository users;
	private final EducationUserBindingRepository bindings;
	private final EducationResourceTransactionLock locks;
	private final IAuditLogService audit;

	public record AccountLink(String type, String code, String username) {}

	@Transactional
	public TeacherAcademicProfile registerTeacher(String schoolId, String localCode, String displayName, String actor) {
		campuses(schoolId, actor);
		var school = organizations.findById(schoolId).orElseThrow();
		String code = required(school.getOrgCode() + ":" + required(localCode, "编码", 32), "机构组合编码", 64);
		String name = required(displayName, "姓名", 100);
		locks.lockSemester("assessment-teacher:" + schoolId);
		if (employees.findByEmployeeCode(code).isPresent()) {
			throw new IllegalStateException("该教师编号已存在，请选择已有人员");
		}
		var employee = new Employee();
		employee.setEmployeeCode(code);
		employee.setEmployeeName(name);
		employee.setEmployeeType("TEACHER");
		employee = employees.save(employee);
		var teacher = new TeacherAcademicProfile();
		teacher.setDepartmentId(schoolId);
		teacher.setTeacherNo(code);
		teacher.setTeacherName(name);
		teacher.setEmployeeId(employee.getId());
		teacher = teachers.save(teacher);
		audit.log(actor, "EDUCATION_ASSESSMENT_TEACHER_REGISTERED", "schoolId=" + schoolId + ",teacherId=" + teacher.getId());
		return teacher;
	}

	@Transactional
	public void linkAccount(String schoolId, AccountLink command, String actor) {
		var campuses = campuses(schoolId, actor);
		String username = required(command.username(), "账号", 100);
		var account = users.findByUsername(username);
		if (account == null || !schoolId.equals(account.getOrganizationId()) || !Integer.valueOf(1).equals(account.getStatus())) {
			throw new IllegalArgumentException("请选择本校已启用的账号");
		}
		String code = required(command.code(), "人员编号", 64);
		String profileId;
		switch (command.type()) {
			case "STUDENT" -> {
				var student = students.findByStudentNo(code).orElseThrow(() -> new IllegalArgumentException("学号不存在"));
				scopes.assertStudentInSchool(scopes.resolve(actor), schoolId, student);
				profileId = student.getId();
			}
			case "TEACHER" -> {
				var teacher = teachers.findByTeacherNo(code).filter(item -> campuses.contains(item.getDepartmentId()))
						.orElseThrow(() -> new IllegalArgumentException("本校教师编号不存在"));
				if (account.getEmployeeId() != null && !teacher.getEmployeeId().equals(account.getEmployeeId())) {
					throw new IllegalStateException("该账号已关联其他员工");
				}
				if (users.findByEmployeeId(teacher.getEmployeeId()).filter(item -> !username.equals(item.getUsername())).isPresent()) {
					throw new IllegalStateException("该教师已关联其他账号");
				}
				account.setEmployeeId(teacher.getEmployeeId());
				users.save(account);
				profileId = teacher.getId();
			}
			default -> throw new IllegalArgumentException("请选择学生或教师");
		}
		var existing = bindings.findByProfileTypeAndProfileId(command.type(), profileId);
		if (existing.filter(item -> !username.equals(item.getUsername())).isPresent()
				|| bindings.findByUsername(username).filter(item -> !command.type().equals(item.getProfileType())
						|| !profileId.equals(item.getProfileId())).isPresent()) {
			throw new IllegalStateException("人员与账号必须一对一绑定");
		}
		var binding = existing.orElseGet(EducationUserBinding::new);
		binding.setUsername(username);
		binding.setProfileId(profileId);
		binding.setProfileType(command.type());
		binding.setStatus("ACTIVE");
		binding.setInvalidatedAt(null);
		bindings.save(binding);
		audit.log(actor, "EDUCATION_ASSESSMENT_ACCOUNT_LINKED", "schoolId=" + schoolId + ",profileId=" + profileId);
	}

	private Set<String> campuses(String schoolId, String actor) {
		var scope = scopes.resolve(actor);
		if (!scope.fullAccess() && !scope.campusIds().contains(schoolId)) {
			throw new AccessDeniedException("无权访问该学校的考核身份");
		}
		if (organizations.findById(schoolId).filter(item -> "SCHOOL".equals(item.getOrganizationType())
				&& Integer.valueOf(1).equals(item.getStatus())).isEmpty()) {
			throw new IllegalArgumentException("请选择启用的学校");
		}
		return scopes.scheduleOrganizationCampuses(scope, schoolId);
	}

	private static String required(String value, String label, int max) {
		if (value == null || value.isBlank() || value.strip().length() > max) {
			throw new IllegalArgumentException(label + "不能为空或超过 " + max + " 个字符");
		}
		return value.strip();
	}
}
