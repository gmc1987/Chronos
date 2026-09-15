package com.chronos.education.scheduling.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.ExamCandidate;
import com.chronos.education.scheduling.model.ExamCourseSuspensionItem;
import com.chronos.education.scheduling.model.ExamCourseSuspensionRequest;
import com.chronos.education.scheduling.model.ExamCommands;
import com.chronos.education.scheduling.model.ExamDutyView;
import com.chronos.education.scheduling.model.ExamInvigilation;
import com.chronos.education.scheduling.model.ExamInvigilationChange;
import com.chronos.education.scheduling.model.ExamPlan;
import com.chronos.education.scheduling.model.ExamPublishedChange;
import com.chronos.education.scheduling.model.ExamRoom;
import com.chronos.education.scheduling.model.ExamSession;
import com.chronos.education.scheduling.model.ExamStudentView;
import com.chronos.education.scheduling.model.ExamTeacherSuggestion;
import com.chronos.education.scheduling.model.ExamTeacherQualification;
import com.chronos.education.scheduling.model.ScheduleOccurrenceView;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.ExamCenterService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ExamCenterController {
	private final ExamCenterService service;
	private final EducationDataScopeService scopes;

	@GetMapping("/portal/education/exam/my-exams")
	@PreAuthorize("isAuthenticated()")
	public ResultData<List<ExamStudentView>> myExams(Authentication authentication) {
		return ok(service.myExams(authentication.getName()));
	}

	@GetMapping("/portal/education/exam/my-invigilations")
	@PreAuthorize("isAuthenticated()")
	public ResultData<List<ExamDutyView>> myInvigilations(Authentication authentication) {
		return ok(service.myDuties(authentication.getName()));
	}

	@PostMapping("/portal/education/exam/my-invigilations/{id}/acknowledge")
	@PreAuthorize("isAuthenticated()")
	public ResultData<ExamInvigilation> acknowledgeDuty(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.acknowledgeDuty(id, authentication.getName()));
	}

	@PostMapping("/portal/education/exam/my-invigilations/{id}/check-in")
	@PreAuthorize("isAuthenticated()")
	public ResultData<ExamInvigilation> checkInDuty(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.checkInDuty(id, authentication.getName()));
	}

	@GetMapping("/portal/education/exam/my-change-requests")
	@PreAuthorize("isAuthenticated()")
	public ResultData<List<ExamInvigilationChange>> myChangeRequests(
			Authentication authentication) {
		return ok(service.myChanges(authentication.getName()));
	}

	@PostMapping("/portal/education/exam/my-invigilations/{id}/change-requests")
	@PreAuthorize("isAuthenticated()")
	public ResultData<ExamInvigilationChange> requestOwnChange(
			@PathVariable String id,
			@RequestBody ExamCommands.Change command,
			Authentication authentication) {
		return ok(service.requestChange(id, command, authentication.getName(), false));
	}

	@GetMapping("/admin/education/exam/plans")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:view','education:exam:plan:manage','education:exam:room:manage','education:exam:invigilation:view','education:exam:invigilation:manage','education:exam:paper-analysis:view','education:exam:paper-analysis:manage')")
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

	@DeleteMapping("/admin/education/exam/plans/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:delete','education:exam:plan:manage')")
	public ResultData<Void> deletePlan(
			@PathVariable String id,
			Authentication authentication) {
		requireFullAccess(authentication);
		service.deletePlan(id);
		return ok(null);
	}

	@GetMapping("/admin/education/exam/plans/{id}/sessions")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:view','education:exam:plan:manage','education:exam:room:manage','education:exam:invigilation:view','education:exam:invigilation:manage','education:exam:paper-analysis:view','education:exam:paper-analysis:manage')")
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

	@PutMapping("/admin/education/exam/plans/{planId}/sessions/{sessionId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:update','education:exam:plan:manage')")
	public ResultData<ExamSession> updateSession(
			@PathVariable String planId,
			@PathVariable String sessionId,
			@RequestBody ExamCommands.Session command,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.saveSession(planId, sessionId, command));
	}

	@DeleteMapping("/admin/education/exam/plans/{planId}/sessions/{sessionId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:delete','education:exam:plan:manage')")
	public ResultData<Void> deleteSession(
			@PathVariable String planId,
			@PathVariable String sessionId,
			Authentication authentication) {
		requireFullAccess(authentication);
		service.deleteSession(planId, sessionId);
		return ok(null);
	}

	@GetMapping("/admin/education/exam/sessions/{id}/rooms")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:view','education:exam:plan:manage','education:exam:room:manage','education:exam:invigilation:view','education:exam:invigilation:manage','education:exam:paper-analysis:view','education:exam:paper-analysis:manage')")
	public ResultData<List<ExamRoom>> rooms(
			@PathVariable String id,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.rooms(id));
	}

	@PostMapping("/admin/education/exam/sessions/{id}/rooms")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:room:manage','education:exam:plan:manage')")
	public ResultData<ExamRoom> addRoom(
			@PathVariable String id,
			@RequestBody ExamCommands.Room command,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.addRoom(id, command));
	}

	@DeleteMapping("/admin/education/exam/sessions/{sessionId}/rooms/{roomId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:room:delete','education:exam:room:manage','education:exam:plan:manage')")
	public ResultData<Void> deleteRoom(
			@PathVariable String sessionId,
			@PathVariable String roomId,
			Authentication authentication) {
		requireFullAccess(authentication);
		service.deleteRoom(sessionId, roomId);
		return ok(null);
	}

	@GetMapping("/admin/education/exam/rooms/{id}/candidates")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:view','education:exam:plan:manage','education:exam:room:manage','education:exam:paper-analysis:view','education:exam:paper-analysis:manage')")
	public ResultData<List<ExamCandidate>> candidates(
			@PathVariable String id,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.candidates(id));
	}

	@PostMapping("/admin/education/exam/rooms/{id}/candidates")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:room:manage','education:exam:plan:manage')")
	public ResultData<List<ExamCandidate>> addCandidates(
			@PathVariable String id,
			@RequestBody ExamCommands.Candidates command,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.addCandidates(id, command));
	}

	@PostMapping("/admin/education/exam/rooms/{id}/class-candidates")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:room:manage','education:exam:plan:manage')")
	public ResultData<List<ExamCandidate>> addClassCandidates(
			@PathVariable String id,
			@RequestParam String administrativeClassId,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.addClassCandidates(id, administrativeClassId));
	}

	@DeleteMapping("/admin/education/exam/rooms/{roomId}/candidates/{candidateId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:room:delete','education:exam:room:manage','education:exam:plan:manage')")
	public ResultData<Void> removeCandidate(
			@PathVariable String roomId,
			@PathVariable String candidateId,
			Authentication authentication) {
		requireFullAccess(authentication);
		service.removeCandidate(roomId, candidateId);
		return ok(null);
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
	public ResultData<List<ExamTeacherSuggestion>> availableTeachers(
			@PathVariable String id,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.availableTeachers(id));
	}

	@GetMapping("/admin/education/exam/sessions/{id}/standbys")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:invigilation:view','education:exam:invigilation:manage')")
	public ResultData<List<ExamInvigilation>> standbys(
			@PathVariable String id,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.standbys(id));
	}

	@PostMapping("/admin/education/exam/rooms/{roomId}/standbys")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:invigilation:manage')")
	public ResultData<ExamInvigilation> addStandby(
			@PathVariable String roomId,
			@RequestParam String teacherId,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.addStandby(roomId, teacherId));
	}

	@PostMapping("/admin/education/exam/invigilations/{id}/mark-absent")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:invigilation:manage')")
	public ResultData<ExamInvigilation> markAbsent(
			@PathVariable String id,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.markAbsent(id, authentication.getName()));
	}

	@PostMapping("/admin/education/exam/plans/{id}/auto-invigilation")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:invigilation:manage')")
	public ResultData<List<ExamInvigilation>> autoAssign(
			@PathVariable String id,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.autoAssign(id));
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

	@DeleteMapping("/admin/education/exam/rooms/{roomId}/invigilators/{assignmentId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:invigilation:delete','education:exam:invigilation:manage')")
	public ResultData<Void> unassign(
			@PathVariable String roomId,
			@PathVariable String assignmentId,
			Authentication authentication) {
		requireFullAccess(authentication);
		service.unassign(roomId, assignmentId);
		return ok(null);
	}

	@PostMapping("/admin/education/exam/plans/{id}/publish")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:plan:manage')")
	public ResultData<ExamPlan> publish(
			@PathVariable String id,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.publish(id, authentication.getName()));
	}

	@GetMapping("/admin/education/exam/plans/{id}/conflicts")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:view','education:exam:plan:manage')")
	public ResultData<List<String>> conflicts(
			@PathVariable String id,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.previewConflicts(id));
	}

	@GetMapping("/admin/education/exam/plans/{id}/suspension-impacts")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:view','education:exam:plan:manage','education:exam:suspension:approve')")
	public ResultData<List<ScheduleOccurrenceView>> suspensionImpacts(
			@PathVariable String id,
			@RequestParam String scopeMode,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.suspensionImpacts(id, scopeMode));
	}

	@GetMapping("/admin/education/exam/plans/{id}/suspension-requests")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:view','education:exam:plan:manage','education:exam:suspension:approve')")
	public ResultData<List<ExamCourseSuspensionRequest>> suspensionRequests(
			@PathVariable String id,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.suspensionRequests(id));
	}

	@GetMapping("/admin/education/exam/suspension-requests/{id}/items")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:view','education:exam:plan:manage','education:exam:suspension:approve')")
	public ResultData<List<ExamCourseSuspensionItem>> suspensionItems(
			@PathVariable String id,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.suspensionItems(id));
	}

	@PostMapping("/admin/education/exam/plans/{id}/suspension-requests")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:plan:manage')")
	public ResultData<ExamCourseSuspensionRequest> requestSuspension(
			@PathVariable String id,
			@RequestBody SuspensionRequestCommand command,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.requestSuspension(
				id, command.scopeMode(), command.reason(), authentication.getName()));
	}

	@PostMapping("/admin/education/exam/suspension-requests/{id}/decide")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:suspension:approve')")
	public ResultData<ExamCourseSuspensionRequest> decideSuspension(
			@PathVariable String id,
			@RequestBody SuspensionDecisionCommand command,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.decideSuspension(id, command.approve(), authentication.getName()));
	}

	public record SuspensionRequestCommand(String scopeMode, String reason) {
	}

	public record SuspensionDecisionCommand(boolean approve) {
	}

	@GetMapping("/admin/education/exam/plans/{id}/published-changes")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:plan:view','education:exam:plan:manage','education:exam:change:approve')")
	public ResultData<List<ExamPublishedChange>> publishedChanges(
			@PathVariable String id,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.publishedChanges(id));
	}

	@PostMapping("/admin/education/exam/plans/{id}/published-changes")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:plan:manage')")
	public ResultData<ExamPublishedChange> requestPublishedChange(
			@PathVariable String id,
			@RequestBody ExamCommands.PublishedChange command,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.requestPublishedChange(id, command, authentication.getName()));
	}

	@PostMapping("/admin/education/exam/published-changes/{id}/decide")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:change:approve')")
	public ResultData<ExamPublishedChange> decidePublishedChange(
			@PathVariable String id,
			@RequestBody SuspensionDecisionCommand command,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.decidePublishedChange(
				id, command.approve(), authentication.getName()));
	}

	@GetMapping("/admin/education/exam/invigilation-changes")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:invigilation:view','education:exam:invigilation:manage')")
	public ResultData<List<ExamInvigilationChange>> changes(Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.pendingChanges());
	}

	@GetMapping("/admin/education/exam/teachers/{teacherId}/qualifications")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:invigilation:view','education:exam:invigilation:manage')")
	public ResultData<List<ExamTeacherQualification>> teacherQualifications(
			@PathVariable String teacherId,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.teacherQualifications(teacherId));
	}

	@PostMapping("/admin/education/exam/teachers/{teacherId}/qualifications/{subjectId}")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:invigilation:manage')")
	public ResultData<ExamTeacherQualification> addTeacherQualification(
			@PathVariable String teacherId,
			@PathVariable String subjectId,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.addTeacherQualification(teacherId, subjectId));
	}

	@DeleteMapping("/admin/education/exam/teachers/{teacherId}/qualifications/{subjectId}")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:invigilation:manage')")
	public ResultData<Void> removeTeacherQualification(
			@PathVariable String teacherId,
			@PathVariable String subjectId,
			Authentication authentication) {
		requireFullAccess(authentication);
		service.removeTeacherQualification(teacherId, subjectId);
		return ok(null);
	}

	@PostMapping("/admin/education/exam/invigilations/{id}/change-requests")
	@PreAuthorize("isAuthenticated()")
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
		return ok(service.emergencyChange(id, command, authentication.getName()));
	}

	private void requireFullAccess(Authentication authentication) {
		scopes.assertFullAccess(scopes.resolve(authentication.getName()));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
