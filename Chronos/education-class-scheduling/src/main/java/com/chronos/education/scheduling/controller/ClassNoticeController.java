package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.ClassNotice;
import com.chronos.education.scheduling.model.ClassNoticeRecipient;
import com.chronos.education.scheduling.service.ClassNoticeService;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class ClassNoticeController {
	private final ClassNoticeService service;
	public ClassNoticeController(ClassNoticeService service) {
		this.service = service;
	}

	@GetMapping("/portal/education/head-teacher/classes/{classId}/notices")
	@PreAuthorize("isAuthenticated()")
	public ResultData<List<Map<String, Object>>> teacherNotices(
			@PathVariable String classId,
			Principal principal) {
		return ok(service.teacherNotices(principal.getName(), classId));
	}

	@PostMapping("/portal/education/head-teacher/classes/{classId}/notices")
	@PreAuthorize("isAuthenticated()")
	public ResultData<ClassNotice> create(
			@PathVariable String classId,
			@RequestBody ClassNotice command,
			Principal principal) {
		return ok(service.create(principal.getName(), classId, command));
	}

	@PostMapping("/portal/education/head-teacher/notices/{id}/publish")
	@PreAuthorize("isAuthenticated()")
	public ResultData<ClassNotice> publish(@PathVariable String id, Principal principal) {
		return ok(service.publish(principal.getName(), id));
	}

	@GetMapping("/portal/education/class-notices")
	@PreAuthorize("isAuthenticated()")
	public ResultData<List<Map<String, Object>>> parentNotices(Principal principal) {
		return ok(service.parentNotices(principal.getName()));
	}

	@PostMapping("/portal/education/class-notices/{id}/acknowledge")
	@PreAuthorize("isAuthenticated()")
	public ResultData<ClassNoticeRecipient> acknowledge(
			@PathVariable String id,
			@RequestBody(required = false) Map<String, String> body,
			Principal principal) {
		return ok(service.acknowledge(
				principal.getName(),
				id,
				body == null ? null : body.get("comment")));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
