package com.chronos.education.grade.controller;

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.education.grade.model.GradeChangeIncident;
import com.chronos.education.grade.service.GradeChangeIncidentService;
import com.chronos.education.grade.service.GradeProductionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员查看并恢复成绩更正审批后的业务回写事故。 */
@RestController
@RequestMapping("/admin/education/grade-change-incidents")
public class GradeChangeIncidentController {
	private final GradeChangeIncidentService incidents;
	private final GradeProductionService production;

	public GradeChangeIncidentController(
			GradeChangeIncidentService incidents,
			GradeProductionService production) {
		this.incidents = incidents;
		this.production = production;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('education:score:incident:manage')")
	public ResultData<PageView<GradeChangeIncident>> page(
			@RequestParam(defaultValue = "OPEN") String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			Authentication authentication) {
		return ok(PageView.from(incidents.page(status, page, size, authentication.getName())));
	}

	@PostMapping("/{id}/retry")
	@PreAuthorize("hasAuthority('education:score:incident:manage')")
	public ResultData<GradeChangeIncident> retry(
			@PathVariable String id,
			Authentication authentication) {
		String actor = authentication.getName();
		GradeChangeIncident incident = incidents.requireOpen(id, actor);
		try {
			production.replayApprovedChange(incident.getChangeRequestId(), actor);
		} catch (RuntimeException exception) {
			incidents.markRetryFailed(id, exception.getMessage(), actor);
			throw new IllegalStateException("成绩更正回写重试失败：" + exception.getMessage(), exception);
		}
		return ok(incidents.resolve(id, actor));
	}

	@PostMapping("/{id}/ignore")
	@PreAuthorize("hasAuthority('education:score:incident:manage')")
	public ResultData<GradeChangeIncident> ignore(
			@PathVariable String id,
			@RequestBody IgnoreCommand command,
			Authentication authentication) {
		return ok(incidents.ignore(
			id,
			command == null ? null : command.reason(),
			authentication.getName()));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}

	public record IgnoreCommand(String reason) {
	}
}
