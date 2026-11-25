package com.chronos.education.scheduling.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.ScheduleDateException;
import com.chronos.education.scheduling.service.TeachingSuspensionService;
import com.chronos.education.scheduling.service.ClassSchedulingService;
import com.chronos.education.scheduling.service.AcademicDataService;
import org.springframework.security.access.AccessDeniedException;
import java.util.Set;
import java.util.stream.Collectors;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.ScheduleOccurrenceService;

/** 管理端日期课表与课表例外接口。 */
@RestController
public class ScheduleCalendarController {
	private final ScheduleOccurrenceService service;
	private final EducationDataScopeService dataScopes;
	private final TeachingSuspensionService suspensions;
	private final ClassSchedulingService scheduling;
	private final AcademicDataService academicData;

	public ScheduleCalendarController(
			ScheduleOccurrenceService service,
			EducationDataScopeService dataScopes,
			TeachingSuspensionService suspensions,
			ClassSchedulingService scheduling, AcademicDataService academicData) {
		this.service = service;
		this.dataScopes = dataScopes;
		this.suspensions = suspensions;
		this.scheduling = scheduling;
		this.academicData = academicData;
	}

	@GetMapping("/admin/education/schedule-occurrences")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<?> occurrences(
			@RequestParam String semesterCode,
			@RequestParam(required = false) String organizationId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) String campusId,
			@RequestParam(required = false) String majorId,
			@RequestParam(required = false) String classId,
			Authentication authentication) {
		var scope = dataScopes.resolve(authentication.getName());
		Set<String> allowed = campusId == null && majorId == null && classId == null
				? visibleOfferings(semesterCode, organizationId, authentication)
				: suspensions.targetOfferings(scope, new TeachingSuspensionService.Selection(
						organizationId, semesterCode, date, date, campusId, majorId, classId)).keySet();
		return ok(service.occurrences(semesterCode, date).stream()
				.filter(item -> allowed == null || allowed.contains(item.entry().offeringId())).toList());
	}

	@GetMapping("/admin/education/schedule-date-exceptions")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<?> exceptions(
			@RequestParam String semesterCode,
			@RequestParam(required = false) String organizationId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			Authentication authentication) {
		var allowed = visibleEntries(semesterCode, organizationId, authentication);
		return ok(service.exceptions(semesterCode, startDate, endDate).stream()
				.filter(item -> allowed == null || allowed.contains(item.getSourceEntryId())).toList());
	}

	@GetMapping("/admin/education/schedule-date-exceptions/history")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<?> history(
			@RequestParam String semesterCode,
			@RequestParam(required = false) String organizationId,
			Authentication authentication) {
		var allowed = visibleEntries(semesterCode, organizationId, authentication);
		return ok(service.exceptionHistory(semesterCode).stream()
				.filter(item -> allowed == null || allowed.contains(item.getSourceEntryId())).toList());
	}

	@GetMapping("/admin/education/schedule-pending-makeups")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<?> pendingMakeups(
			@RequestParam String semesterCode,
			@RequestParam(required = false) String organizationId,
			Authentication authentication) {
		var allowed = visibleOfferings(semesterCode, organizationId, authentication);
		return ok(service.pendingMakeups(semesterCode).stream()
				.filter(item -> allowed == null || allowed.contains(item.entry().offeringId())).toList());
	}

	@GetMapping("/admin/education/schedule-pending-substitutions")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<?> pendingSubstitutions(
			@RequestParam String semesterCode,
			@RequestParam(required = false) String organizationId,
			Authentication authentication) {
		var allowed = visibleOfferings(semesterCode, organizationId, authentication);
		return ok(service.pendingTeacherLeaveCoverage(semesterCode).stream()
				.filter(item -> allowed == null || allowed.contains(item.occurrence().entry().offeringId())).toList());
	}

	@PostMapping("/admin/education/teaching-suspensions/preview")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<?> previewSuspension(@RequestBody TeachingSuspensionService.Selection selection,
			Authentication authentication) {
		return ok(suspensions.preview(selection, authentication.getName()));
	}

	@PostMapping("/admin/education/teaching-suspensions/apply")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:update','education:scheduling:manage')")
	public ResultData<?> applySuspension(@RequestBody TeachingSuspensionService.Apply command,
			Authentication authentication) {
		return ok(suspensions.apply(command, authentication.getName()));
	}

	@PostMapping("/admin/education/schedule-date-exceptions")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:update','education:scheduling:manage')")
	public ResultData<?> create(
			@RequestBody ScheduleDateException command,
			Authentication authentication) {
		dataScopes.assertScheduleEntryAccess(
				dataScopes.resolve(authentication.getName()),
				command.getSourceEntryId());
		assertAdjustmentTargets(command, authentication);
		// The workflow listener owns the approval association for a schedule change.
		command.setWorkflowInstanceId(null);
		command.setMakeupRequired(false);
		return ok(service.saveException(null, command));
	}

	@PutMapping("/admin/education/schedule-date-exceptions/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:update','education:scheduling:manage')")
	public ResultData<?> update(
			@PathVariable String id,
			@RequestBody ScheduleDateException command,
			Authentication authentication) {
		dataScopes.assertScheduleEntryAccess(
				dataScopes.resolve(authentication.getName()),
				command.getSourceEntryId());
		ScheduleDateException existing = service.exception(id);
		dataScopes.assertScheduleEntryAccess(dataScopes.resolve(authentication.getName()), existing.getSourceEntryId());
		assertAdjustmentTargets(command, authentication);
		command.setWorkflowInstanceId(existing.getWorkflowInstanceId());
		command.setMakeupRequired(existing.isMakeupRequired());
		return ok(service.saveException(id, command));
	}

	@DeleteMapping("/admin/education/schedule-date-exceptions/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:delete','education:scheduling:manage')")
	public ResultData<?> cancel(
			@PathVariable String id,
			Authentication authentication) {
		dataScopes.assertScheduleEntryAccess(
				dataScopes.resolve(authentication.getName()),
				service.exception(id).getSourceEntryId());
		service.cancelException(id);
		return ok(null);
	}

	@PostMapping("/admin/education/schedule-date-exceptions/{id}/restore")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:update','education:scheduling:manage')")
	public ResultData<?> restore(
			@PathVariable String id,
			Authentication authentication) {
		dataScopes.assertScheduleEntryAccess(
				dataScopes.resolve(authentication.getName()),
				service.exception(id).getSourceEntryId());
		return ok(service.restoreException(id));
	}

	@PostMapping("/admin/education/schedule-date-exceptions/{id}/waive-makeup")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:update','education:scheduling:manage')")
	public ResultData<?> waiveMakeup(@PathVariable String id, Authentication authentication) {
		dataScopes.assertScheduleEntryAccess(dataScopes.resolve(authentication.getName()),
				service.exception(id).getSourceEntryId());
		service.waiveSuspensionMakeup(id);
		return ok(null);
	}

	private Set<String> visibleOfferings(String term, String organizationId, Authentication auth) {
		var scope = dataScopes.resolve(auth.getName());
		// Preserve the existing unfiltered operations for privileged integrations.
		if (organizationId == null && scope.fullAccess()) return null;
		return suspensions.visibleOfferings(scope, organizationId, term);
	}

	private Set<String> visibleEntries(String term, String organizationId, Authentication auth) {
		var allowed = visibleOfferings(term, organizationId, auth);
		if (allowed == null) return null;
		var visible = scheduling.schedule(term).stream()
				.filter(entry -> allowed.contains(entry.offeringId()))
				.map(entry -> entry.id()).collect(Collectors.toSet());
		return visible;
	}

	private void assertAdjustmentTargets(ScheduleDateException command, Authentication auth) {
		var scope = dataScopes.resolve(auth.getName());
		if (command.getTargetClassroomId() != null) {
			dataScopes.assertClassroomAccess(scope, command.getTargetClassroomId());
		}
		if (command.getSubstituteTeacherId() != null && !scope.fullAccess()
				&& !academicData.teachers(scope).stream().anyMatch(
						teacher -> command.getSubstituteTeacherId().equals(teacher.getId()))) {
			throw new AccessDeniedException("无权选择该代课教师");
		}
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
