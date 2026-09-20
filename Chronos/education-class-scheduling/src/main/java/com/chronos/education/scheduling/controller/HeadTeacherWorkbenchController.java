package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.service.HeadTeacherWorkbenchService;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 班主任门户只读聚合接口；班级范围始终由当前登录教师反向解析。 */
@RestController
public class HeadTeacherWorkbenchController {
	private final HeadTeacherWorkbenchService service;

	public HeadTeacherWorkbenchController(HeadTeacherWorkbenchService service) {
		this.service = service;
	}

	@GetMapping("/portal/education/head-teacher/classes")
	@PreAuthorize("isAuthenticated()")
	public ResultData<List<Map<String, Object>>> classes(Principal principal) {
		return ok(service.classes(principal.getName()));
	}

	@GetMapping("/portal/education/head-teacher/classes/{classId}")
	@PreAuthorize("isAuthenticated()")
	public ResultData<Map<String, Object>> detail(
			@PathVariable String classId,
			Principal principal) {
		return ok(service.classDetail(principal.getName(), classId));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder()
				.code("200")
				.msg("ok")
				.data(data)
				.build();
	}
}
