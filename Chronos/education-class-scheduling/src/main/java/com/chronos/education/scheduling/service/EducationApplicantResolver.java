package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.StudentGuardianRepository;
import com.chronos.education.scheduling.model.EducationUserBinding;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

/** 将受认证的平台账号解析为教务领域申请人，避免信任前端传入的人员 ID。 */
@Service
public class EducationApplicantResolver {
	private final EducationUserBindingRepository bindings;
	private final StudentGuardianRepository guardians;

	public EducationApplicantResolver(
			EducationUserBindingRepository bindings,
			StudentGuardianRepository guardians) {
		this.bindings = bindings;
		this.guardians = guardians;
	}

	public String resolve(String username, String applicantType) {
		return resolve(username, applicantType, null);
	}

	public String resolve(String username, String applicantType, String requestedStudentId) {
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
		if ("STUDENT".equals(normalizedType) && guardians != null
				&& requestedStudentId != null && !requestedStudentId.isBlank()) {
			String parentId = bindings.findByUsernameAndProfileTypeAndStatus(username, "PARENT", "ACTIVE")
					.map(EducationUserBinding::getProfileId).orElse(null);
			if (parentId != null && guardians.findByStudentIdAndParentId(requestedStudentId, parentId).isPresent()) {
				return requestedStudentId;
			}
			throw new AccessDeniedException("家长只能为已绑定的学生发起请假");
		}
		throw missingBinding(username, normalizedType);
	}

	private IllegalStateException missingBinding(String username, String applicantType) {
		return new IllegalStateException(
				"账号 " + username + " 未绑定有效的 " + applicantType + " 教务档案，无法生成请假台账");
	}
}
