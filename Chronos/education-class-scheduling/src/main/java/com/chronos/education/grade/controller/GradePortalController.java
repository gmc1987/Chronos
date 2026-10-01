package com.chronos.education.grade.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.grade.dto.GradeDtos.PortalGradeView;
import com.chronos.education.grade.service.GradeCenterService;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/portal/education/grades")
public class GradePortalController {
	private final GradeCenterService service;
	private final EducationUserBindingRepository bindings;

	public GradePortalController(GradeCenterService service, EducationUserBindingRepository bindings) {
		this.service = service;
		this.bindings = bindings;
	}

	@GetMapping
	public ResultData<List<PortalGradeView>> list(Authentication authentication) {
		return ok(service.studentGradeViews(studentId(authentication)));
	}

	@GetMapping("/{id}")
	public ResultData<PortalGradeView> detail(@PathVariable String id, Authentication authentication) {
		return ok(service.studentGradeViews(studentId(authentication)).stream()
			.filter(value -> value.id().equals(id))
			.findFirst()
			.orElseThrow(() -> new AccessDeniedException("成绩不存在或尚未发布")));
	}

	private String studentId(Authentication authentication) {
		return bindings.findByUsernameAndProfileType(authentication.getName(), "STUDENT")
			.filter(value -> "ACTIVE".equals(value.getStatus()))
			.map(value -> value.getProfileId())
			.orElseThrow(() -> new AccessDeniedException("当前账号不是有效学生账号"));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
