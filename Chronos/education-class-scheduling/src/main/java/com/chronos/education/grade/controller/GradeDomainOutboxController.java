package com.chronos.education.grade.controller;

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.education.grade.model.DomainEventOutbox;
import com.chronos.education.grade.service.DomainEventOutboxService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 成绩领域事件死信运维入口。
 *
 * <p>复用平台流程监控权限，避免普通教师通过运维接口重放或忽略已发布成绩事件。</p>
 */
@RestController
@RequestMapping("/admin/education/grade-domain-outbox")
public class GradeDomainOutboxController {
	private final DomainEventOutboxService events;

	public GradeDomainOutboxController(DomainEventOutboxService events) {
		this.events = events;
	}

	@GetMapping("/dead")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:monitor:view','workflow:manage')")
	public ResultData<PageView<DomainEventOutbox>> dead(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		// 对外只暴露稳定分页结构，避免 Spring Data 升级改变 PageImpl 的 JSON 字段。
		return ok(PageView.from(events.deadEvents(page, size)));
	}

	@PostMapping("/{id}/retry")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:instance:manage','workflow:manage')")
	public ResultData<DomainEventOutbox> retry(@PathVariable String id) {
		return ok(events.retry(id));
	}

	@PostMapping("/{id}/ignore")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:instance:manage','workflow:manage')")
	public ResultData<DomainEventOutbox> ignore(@PathVariable String id) {
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
