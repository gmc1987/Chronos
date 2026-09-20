package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.model.EducationUserBinding;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 将登录用户名转换为稳定的教育领域档案 ID，禁止直接比较两种不同标识。 */
@Service
@Transactional(readOnly = true)
public class EducationIdentityService {
	private final EducationUserBindingRepository bindings;

	public EducationIdentityService(EducationUserBindingRepository bindings) {
		this.bindings = bindings;
	}

	public Set<String> teacherIds(String username) {
		return bindings.findByUsernameAndStatusOrderByProfileType(username, "ACTIVE").stream()
				.filter(value -> "TEACHER".equals(value.getProfileType()))
				.map(EducationUserBinding::getProfileId)
				.collect(Collectors.toUnmodifiableSet());
	}

	public boolean isTeacher(String username, String teacherId) {
		return teacherId != null && teacherIds(username).contains(teacherId);
	}
}
