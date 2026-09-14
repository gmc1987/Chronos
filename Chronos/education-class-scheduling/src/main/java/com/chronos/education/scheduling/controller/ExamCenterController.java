package com.chronos.education.scheduling.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.ExamCandidate;
import com.chronos.education.scheduling.model.ExamCommands;
import com.chronos.education.scheduling.model.ExamInvigilation;
import com.chronos.education.scheduling.model.ExamInvigilationChange;
import com.chronos.education.scheduling.model.ExamPlan;
import com.chronos.education.scheduling.model.ExamRoom;
import com.chronos.education.scheduling.model.ExamSession;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.ExamCenterService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ExamCenterController {
	private final ExamCenterService service;
	private final EducationDataScopeService scopes;

	@GetMapping("/admin/education/exam/plans")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:view','education:exam:plan:manage')")
	public ResultData<List<ExamPlan>> plans(
			@RequestParam String semesterCode,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.plans(semesterCode));
	}

	@PostMapping("/admin/education/exam/plans")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:create','education:exam:plan:manage')")
	public ResultData<ExamPlan> createPlan(
			@RequestBody ExamCommands.Plan command,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.savePlan(null, command));
	}

	@PutMapping("/admin/education/exam/plans/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:update','education:exam:plan:manage')")
	public ResultData<ExamPlan> updatePlan(
			@PathVariable String id,
			@RequestBody ExamCommands.Plan command,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.savePlan(id, command));
	}

	@GetMapping("/admin/education/exam/plans/{id}/sessions")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:view','education:exam:plan:manage')")
	public ResultData<List<ExamSession>> sessions(
			@PathVariable String id,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.sessions(id));
	}

	@PostMapping("/admin/education/exam/plans/{id}/sessions")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:create','education:exam:plan:manage')")
	public ResultData<ExamSession> addSession(
			@PathVariable String id,
			@RequestBody ExamCommands.Session command,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.addSession(id, command));
	}

	@GetMapping("/admin/education/exam/sessions/{id}/rooms")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:view','education:exam:plan:manage')")
	public ResultData<List<ExamRoom>> rooms(
			@PathVariable String id,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.rooms(id));
	}

	@PostMapping("/admin/education/exam/sessions/{id}/rooms")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:create','education:exam:plan:manage')")
	public ResultData<ExamRoom> addRoom(
			@PathVariable String id,
			@RequestBody ExamCommands.Room command,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.addRoom(id, command));
	}

	@GetMapping("/admin/education/exam/rooms/{id}/candidates")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:view','education:exam:plan:manage')")
	public ResultData<List<ExamCandidate>> candidates(
			@PathVariable String id,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.candidates(id));
	}

	@PostMapping("/admin/education/exam/rooms/{id}/candidates")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:create','education:exam:plan:manage')")
	public ResultData<List<ExamCandidate>> addCandidates(
			@PathVariable String id,
			@RequestBody ExamCommands.Candidates command,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.addCandidates(id, command));
	}

	@GetMapping("/admin/education/exam/rooms/{id}/invigilators")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:invigilation:view','education:exam:invigilation:manage')")
	public ResultData<List<ExamInvigilation>> assignments(
			@PathVariable String id,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.assignments(id));
	}

	@GetMapping("/admin/education/exam/rooms/{id}/available-teachers")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:invigilation:view','education:exam:invigilation:manage')")
	public ResultData<List<TeacherAcademicProfile>> availableTeachers(
			@PathVariable String id,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.availableTeachers(id));
	}

	@PostMapping("/admin/education/exam/rooms/{id}/invigilators")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:invigilation:create','education:exam:invigilation:manage')")
	public ResultData<ExamInvigilation> assign(
			@PathVariable String id,
			@RequestBody ExamCommands.Assignment command,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.assign(id, command));
	}

	@PostMapping("/admin/education/exam/plans/{id}/publish")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:plan:manage')")
	public ResultData<ExamPlan> publish(
			@PathVariable String id,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.publish(id));
	}

	@GetMapping("/admin/education/exam/invigilation-changes")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:invigilation:view','education:exam:invigilation:manage')")
	public ResultData<List<ExamInvigilationChange>> changes(Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.pendingChanges());
	}

	@PostMapping("/admin/education/exam/invigilations/{id}/change-requests")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:invigilation:update','education:exam:invigilation:manage')")
	public ResultData<ExamInvigilationChange> requestChange(
			@PathVariable String id,
			@RequestBody ExamCommands.Change command,
			Authentication authentication) {
		return ok(service.requestChange(id, command, authentication.getName(), false));
	}

	@PostMapping("/admin/education/exam/invigilation-changes/{id}/decide")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:invigilation:manage')")
	public ResultData<ExamInvigilationChange> decideChange(
			@PathVariable String id,
			@RequestBody ExamCommands.Decision command,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.decideChange(id, command, authentication.getName()));
	}

	@PostMapping("/admin/education/exam/invigilations/{id}/emergency-change")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:invigilation:manage')")
	public ResultData<ExamInvigilationChange> emergencyChange(
			@PathVariable String id,
			@RequestBody ExamCommands.Change command,
			Authentication authentication) {
		requireFullAccess(authentication);
		ExamInvigilationChange change = service.requestChange(
				id, command, authentication.getName(), true);
		return ok(service.decideChange(
				change.getId(),
				new ExamCommands.Decision(true, command.proposedTeacherId()),
				authentication.getName()));
	}

	private void requireFullAccess(Authentication authentication) {
		scopes.assertFullAccess(scopes.resolve(authentication.getName()));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
