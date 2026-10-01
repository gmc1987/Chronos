package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.EducationDomainOutbox;
import com.chronos.education.scheduling.service.EducationDomainEventService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 教育领域死信沿用流程监控权限，统一由平台运维人员处理。 */
@RestController
@RequestMapping("/admin/education/domain-outbox")
public class EducationDomainOutboxController {
	private final EducationDomainEventService events;

	public EducationDomainOutboxController(EducationDomainEventService events) {
		this.events = events;
	}

	@GetMapping("/dead")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:monitor:view','workflow:manage')")
	public ResultData<PageView<EducationDomainOutbox>> dead(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		// 三类死信列表共用平台分页契约，前端切换标签时不需要兼容不同结构。
		return ok(PageView.from(events.deadEvents(page, size)));
	}

	@PostMapping("/{id}/retry")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:instance:manage','workflow:manage')")
	public ResultData<EducationDomainOutbox> retry(@PathVariable String id) {
		return ok(events.retry(id));
	}

	@PostMapping("/{id}/ignore")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:instance:manage','workflow:manage')")
	public ResultData<EducationDomainOutbox> ignore(@PathVariable String id) {
		return ok(events.ignore(id));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder()
				.code("200")
				.msg("success")
				.data(data)
				.build();
	}
}
