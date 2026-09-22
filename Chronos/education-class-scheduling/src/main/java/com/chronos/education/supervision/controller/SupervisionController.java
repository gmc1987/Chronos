package com.chronos.education.supervision.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.supervision.dto.SupervisionDtos.*;
import com.chronos.education.supervision.model.*;
import com.chronos.education.supervision.service.SupervisionCenterService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class SupervisionController {
	private final SupervisionCenterService service;

	public SupervisionController(SupervisionCenterService service) {
		this.service = service;
	}

	@GetMapping("/admin/education/supervision/plans")
	@PreAuthorize("hasAuthority('education:supervision:plan:view')")
	public ResultData<List<PlanResponse>> plans(Authentication auth) {
		return ok(service.listPlans(auth.getName()).stream().map(this::plan).toList());
	}

	@PostMapping("/admin/education/supervision/plans")
	@PreAuthorize("hasAuthority('education:supervision:plan:create')")
	public ResultData<PlanResponse> createPlan(@Valid @RequestBody PlanCommand command, Authentication auth) {
		return ok(plan(service.createPlan(auth.getName(), command.name(), command.startDate(),
				command.endDate(), command.campusId())));
	}

	@PostMapping("/admin/education/supervision/plans/{id}/publish")
	@PreAuthorize("hasAuthority('education:supervision:plan:publish')")
	public ResultData<PlanResponse> publishPlan(@PathVariable String id, Authentication auth) {
		return ok(plan(service.publishPlan(id, auth.getName())));
	}

	@PostMapping("/admin/education/supervision/plans/{id}/start")
	@PreAuthorize("hasAuthority('education:supervision:plan:update')")
	public ResultData<PlanResponse> startPlan(@PathVariable String id, Authentication auth) {
		return ok(plan(service.startPlan(id, auth.getName())));
	}

	@PostMapping("/admin/education/supervision/plans/{id}/complete")
	@PreAuthorize("hasAuthority('education:supervision:plan:update')")
	public ResultData<PlanResponse> completePlan(@PathVariable String id, Authentication auth) {
		return ok(plan(service.completePlan(id, auth.getName())));
	}

	@PostMapping("/admin/education/supervision/plans/{id}/archive")
	@PreAuthorize("hasAuthority('education:supervision:plan:update')")
	public ResultData<PlanResponse> archivePlan(@PathVariable String id, Authentication auth) {
		return ok(plan(service.archivePlan(id, auth.getName())));
	}

	@PostMapping("/admin/education/supervision/assignments")
	@PreAuthorize("hasAuthority('education:supervision:assignment:create')")
	public ResultData<AssignmentResponse> assign(@Valid @RequestBody AssignmentCommand command, Authentication auth) {
		return ok(assignment(service.createAssignment(auth.getName(), command.planId(),
				command.supervisorId(), command.teacherId(), command.scheduleEntryId(), command.campusId())));
	}

	@GetMapping("/portal/education/supervision/tasks")
	@PreAuthorize("hasAuthority('education:supervision:assignment:view')")
	public ResultData<List<AssignmentResponse>> myTasks(Authentication auth) {
		return ok(service.myAssignments(auth.getName()).stream().map(this::assignment).toList());
	}

	@PostMapping("/portal/education/supervision/tasks/{id}/accept")
	@PreAuthorize("hasAuthority('education:supervision:assignment:accept')")
	public ResultData<AssignmentResponse> accept(@PathVariable String id, Authentication auth) {
		return ok(assignment(service.accept(id, auth.getName())));
	}

	@PostMapping("/portal/education/supervision/tasks/{id}/check-in")
	@PreAuthorize("hasAuthority('education:supervision:assignment:check-in')")
	public ResultData<AssignmentResponse> checkIn(@PathVariable String id,
			@RequestBody(required = false) CheckInCommand command, Authentication auth) {
		return ok(assignment(service.checkIn(id, auth.getName(), command == null ? null : command.proof())));
	}

	@PostMapping("/portal/education/supervision/tasks/{id}/complete")
	@PreAuthorize("hasAuthority('education:supervision:assignment:update')")
	public ResultData<AssignmentResponse> complete(@PathVariable String id, Authentication auth) {
		return ok(assignment(service.completeAssignment(id, auth.getName())));
	}

	@PostMapping("/portal/education/supervision/tasks/{id}/submit")
	@PreAuthorize("hasAuthority('education:supervision:record:create')")
	public ResultData<RecordResponse> submit(@PathVariable String id, @Valid @RequestBody EvaluationCommand command,
			Authentication auth) {
		return ok(record(service.submit(id, auth.getName(), null, null, command.formTemplateId())));
	}

	@GetMapping("/portal/education/supervision/tasks/{id}/record")
	@PreAuthorize("hasAuthority('education:supervision:record:view')")
	public ResultData<RecordResponse> record(@PathVariable String id, Authentication auth) {
		return ok(record(service.getRecordForSupervisor(id, auth.getName())));
	}

	@PostMapping("/admin/education/supervision/records/{recordId}/issues")
	@PreAuthorize("hasAuthority('education:supervision:issue:create')")
	public ResultData<IssueResponse> issue(@PathVariable String recordId, @Valid @RequestBody IssueCommand command,
			Authentication auth) {
		return ok(issue(service.createIssue(recordId, auth.getName(), command.severity(), command.title(),
				command.description(), command.ownerId(), command.dueAt())));
	}

	@PostMapping("/portal/education/supervision/issues/{id}/rectify")
	@PreAuthorize("hasAuthority('education:supervision:rectification:create')")
	public ResultData<IssueResponse> rectify(@PathVariable String id, @Valid @RequestBody RectificationCommand command,
			Authentication auth) {
		service.rectify(id, auth.getName(), command.content());
		return ok(issue(service.getIssue(id)));
	}

	@PostMapping("/admin/education/supervision/issues/{id}/review")
	@PreAuthorize("hasAuthority('education:supervision:review:create')")
	public ResultData<IssueResponse> review(@PathVariable String id, @Valid @RequestBody ReviewCommand command,
			Authentication auth) {
		return ok(issue(service.review(id, auth.getName(), command.approved(), command.comment())));
	}

	private PlanResponse plan(SupervisionPlan value) {
		return new PlanResponse(value.getId(), value.getName(), value.getStatus(), value.getStartDate(), value.getEndDate());
	}
	private AssignmentResponse assignment(SupervisionAssignment value) {
		return new AssignmentResponse(value.getId(), value.getPlanId(), value.getTeacherId(), value.getScheduleEntryId(),
				value.getStatus(), value.getCheckedInAt(), value.getSubmittedAt());
	}
	private RecordResponse record(SupervisionRecord value) {
		return new RecordResponse(value.getId(), value.getAssignmentId(), value.getSupervisorId(), value.getTeacherId(),
				value.getFormTemplateId(), value.getFormSnapshotJson(), value.getScheduleContextSnapshotJson(), value.getSubmittedAt());
	}
	private IssueResponse issue(SupervisionIssue value) {
		return new IssueResponse(value.getId(), value.getRecordId(), value.getSeverity(), value.getTitle(), value.getStatus(),
				value.getOwnerId(), value.getDueAt());
	}
	private <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("ok").data(value).build();
	}
}
