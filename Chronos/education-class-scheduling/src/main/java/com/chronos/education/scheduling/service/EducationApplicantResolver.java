package com.chronos.education.scheduling.service;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.EducationUserBinding;
import org.springframework.stereotype.Service;

/** 将受认证的平台账号解析为教务领域申请人，避免信任前端传入的人员 ID。 */
@Service
public class EducationApplicantResolver {
	private final EducationUserBindingRepository bindings;
	private final IAdminUserRepository users;
	private final TeacherAcademicProfileRepository teachers;

	public EducationApplicantResolver(
			EducationUserBindingRepository bindings,
			IAdminUserRepository users,
			TeacherAcademicProfileRepository teachers) {
		this.bindings = bindings;
		this.users = users;
		this.teachers = teachers;
	}

	public String resolve(String username, String applicantType) {
		String normalizedType = applicantType == null ? "" : applicantType.trim().toUpperCase();
		String boundProfileId = bindings
				.findByUsernameAndStatusOrderByProfileType(username, "ACTIVE")
				.stream()
				.filter(binding -> normalizedType.equals(binding.getProfileType()))
				.map(EducationUserBinding::getProfileId)
				.findFirst()
				.orElse(null);
		if (boundProfileId != null && !boundProfileId.isBlank()) {
			return boundProfileId;
		}
		if ("TEACHER".equals(normalizedType)) {
			return resolveTeacherFromEmployee(username);
		}
		throw missingBinding(username, normalizedType);
	}

	private String resolveTeacherFromEmployee(String username) {
		var account = users.findByUsername(username);
		if (account == null || account.getEmployeeId() == null || account.getEmployeeId().isBlank()) {
			throw missingBinding(username, "TEACHER");
		}
		return teachers
				.findByEmployeeId(account.getEmployeeId())
				.map(teacher -> teacher.getId())
				.orElseThrow(() -> missingBinding(username, "TEACHER"));
	}

	private IllegalStateException missingBinding(String username, String applicantType) {
		return new IllegalStateException(
				"账号 " + username + " 未绑定有效的 " + applicantType + " 教务档案，无法生成请假台账");
	}
}
