package com.chronos.education.scheduling.controller;

import java.util.List;

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
import com.chronos.education.scheduling.model.AutoScheduleCommand;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.commons.model.PageView;
import com.chronos.education.scheduling.model.ScheduleCandidateView;
import com.chronos.education.scheduling.model.ScheduleDiffView;
import com.chronos.education.scheduling.model.ScheduleEntryCommand;
import com.chronos.education.scheduling.model.ScheduleEntryView;
import com.chronos.education.scheduling.model.SchedulePlanVersionView;
import com.chronos.education.scheduling.model.TeacherTimeConstraint;
import com.chronos.education.scheduling.service.AutoSchedulingService;
import com.chronos.education.scheduling.service.ClassSchedulingService;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.SchedulePlanVersionService;

@RestController
public class ClassSchedulingController {
	private final ClassSchedulingService service;
	private final SchedulePlanVersionService planVersions;
	private final AutoSchedulingService autoScheduling;
	private final EducationDataScopeService dataScopes;

	public ClassSchedulingController(
			ClassSchedulingService service,
			SchedulePlanVersionService planVersions,
			AutoSchedulingService autoScheduling,
			EducationDataScopeService dataScopes) {
		this.service = service;
		this.planVersions = planVersions;
		this.autoScheduling = autoScheduling;
		this.dataScopes = dataScopes;
	}

	@GetMapping("/admin/education/schedule-versions")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<List<SchedulePlanVersionView>> scheduleVersions(
			@RequestParam String semesterCode,
			Authentication authentication) {
		requireFullDataAccess(authentication);
		return ok(planVersions.versions(semesterCode));
	}

	@PostMapping("/admin/education/schedule-versions/publish")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:scheduling:manage')")
	public ResultData<SchedulePlanVersionView> publishSchedule(
			@RequestParam String semesterCode,
			Authentication authentication) {
		requireFullDataAccess(authentication);
		return ok(SchedulePlanVersionView.from(
				planVersions.publish(semesterCode, authentication.getName())));
	}

	@PostMapping("/admin/education/schedule-versions/{id}/rollback")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:scheduling:manage')")
	public ResultData<SchedulePlanVersionView> rollbackSchedule(
			@PathVariable String id,
			Authentication authentication) {
		requireFullDataAccess(authentication);
		return ok(SchedulePlanVersionView.from(
				planVersions.rollback(id, authentication.getName())));
	}

	@GetMapping("/admin/education/schedule-versions/publication-preview")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<ScheduleDiffView> publicationPreview(
			@RequestParam String semesterCode,
			Authentication authentication) {
		requireFullDataAccess(authentication);
		return ok(autoScheduling.publicationPreview(
				semesterCode,
				planVersions.latestPublishedEntries(semesterCode)));
	}

	@PostMapping("/admin/education/schedule-candidates/generate")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:scheduling:manage')")
	public ResultData<List<ScheduleCandidateView>> generateCandidates(
			@RequestBody AutoScheduleCommand command,
			Authentication authentication) {
		requireFullDataAccess(authentication);
		return ok(autoScheduling.generate(command, authentication.getName()));
	}

	@GetMapping("/admin/education/schedule-candidates")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<List<ScheduleCandidateView>> scheduleCandidates(
			@RequestParam String semesterCode,
			Authentication authentication) {
		requireFullDataAccess(authentication);
		return ok(autoScheduling.list(semesterCode));
	}

	@GetMapping("/admin/education/schedule-candidates/{id}/preview")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<ScheduleDiffView> candidatePreview(
			@PathVariable String id,
			Authentication authentication) {
		requireFullDataAccess(authentication);
		return ok(autoScheduling.preview(id));
	}

	@PostMapping("/admin/education/schedule-candidates/{id}/apply")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:scheduling:manage')")
	public ResultData<ScheduleCandidateView> applyCandidate(
			@PathVariable String id,
			Authentication authentication) {
		requireFullDataAccess(authentication);
		return ok(autoScheduling.apply(id, authentication.getName()));
	}

	@PostMapping("/admin/education/schedule-candidates/{id}/discard")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:scheduling:manage')")
	public ResultData<ScheduleCandidateView> discardCandidate(
			@PathVariable String id,
			Authentication authentication) {
		requireFullDataAccess(authentication);
		return ok(autoScheduling.discard(id, authentication.getName()));
	}

	@GetMapping("/admin/education/course-offerings")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<?> offerings(
			@RequestParam String semesterCode,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size,
			Authentication authentication) {
		var scope = dataScopes.resolve(authentication.getName());
		List<CourseOffering> visible = dataScopes.visibleOfferings(
				scope,
				service.offerings(semesterCode));
		return page == null && size == null
				? ok(visible)
				: ok(PageView.from(
						visible,
						page == null ? 0 : page,
						size == null ? 10 : size));
	}

	@PostMapping("/admin/education/course-offerings")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:create','education:scheduling:manage')")
	public ResultData<CourseOffering> createOffering(
			@RequestBody CourseOffering command,
			Authentication authentication) {
		dataScopes.assertOfferingAccess(
				dataScopes.resolve(authentication.getName()),
				command);
		return ok(service.saveOffering(null, command));
	}

	@PutMapping("/admin/education/course-offerings/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:update','education:scheduling:manage')")
	public ResultData<CourseOffering> updateOffering(
			@PathVariable String id,
			@RequestBody CourseOffering command,
			Authentication authentication) {
		var scope = dataScopes.resolve(authentication.getName());
		dataScopes.assertOfferingAccess(scope, id);
		dataScopes.assertOfferingAccess(scope, command);
		return ok(service.saveOffering(id, command));
	}

	@DeleteMapping("/admin/education/course-offerings/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:delete','education:scheduling:manage')")
	public ResultData<Void> deleteOffering(
			@PathVariable String id,
			Authentication authentication) {
		dataScopes.assertOfferingAccess(
				dataScopes.resolve(authentication.getName()),
				id);
		service.deleteOffering(id);
		return ok(null);
	}

	@GetMapping("/admin/education/classrooms")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:resource:venue:view','education:resource:venue:manage')")
	public ResultData<?> classrooms(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size,
			Authentication authentication) {
		List<Classroom> visible = dataScopes.visibleClassrooms(
				dataScopes.resolve(authentication.getName()),
				service.classrooms());
		return page == null && size == null
				? ok(visible)
				: ok(PageView.from(
						visible,
						page == null ? 0 : page,
						size == null ? 10 : size));
	}

	@PostMapping("/admin/education/classrooms")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:resource:venue:create','education:resource:venue:manage')")
	public ResultData<Classroom> createClassroom(
			@RequestBody Classroom command,
			Authentication authentication) {
		dataScopes.assertClassroomAccess(
				dataScopes.resolve(authentication.getName()),
				command);
		return ok(service.saveClassroom(null, command));
	}

	@PutMapping("/admin/education/classrooms/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:resource:venue:update','education:resource:venue:manage')")
	public ResultData<Classroom> updateClassroom(
			@PathVariable String id,
			@RequestBody Classroom command,
			Authentication authentication) {
		var scope = dataScopes.resolve(authentication.getName());
		dataScopes.assertClassroomAccess(scope, id);
		dataScopes.assertClassroomAccess(scope, command);
		return ok(service.saveClassroom(id, command));
	}

	@DeleteMapping("/admin/education/classrooms/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:resource:venue:delete','education:resource:venue:manage')")
	public ResultData<Void> deleteClassroom(
			@PathVariable String id,
			Authentication authentication) {
		dataScopes.assertClassroomAccess(
				dataScopes.resolve(authentication.getName()),
				id);
		service.deleteClassroom(id);
		return ok(null);
	}

	@GetMapping("/admin/education/schedules")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<List<ScheduleEntryView>> schedule(
			@RequestParam String semesterCode,
			@RequestParam(defaultValue = "ALL") String dimension,
			@RequestParam(required = false) String targetId,
			Authentication authentication) {
		dataScopes.assertScheduleDimensionAccess(
				dataScopes.resolve(authentication.getName()),
				dimension,
				targetId);
		return ok(service.schedule(semesterCode, dimension, targetId));
	}

	@PostMapping("/admin/education/schedules")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:create','education:scheduling:manage')")
	public ResultData<ScheduleEntryView> createEntry(
			@RequestBody ScheduleEntryCommand command,
			Authentication authentication) {
		var scope = dataScopes.resolve(authentication.getName());
		dataScopes.assertOfferingAccess(scope, command.offeringId());
		dataScopes.assertClassroomAccess(scope, command.classroomId());
		return ok(service.saveEntry(null, command));
	}

	@PutMapping("/admin/education/schedules/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:update','education:scheduling:manage')")
	public ResultData<ScheduleEntryView> updateEntry(
			@PathVariable String id,
			@RequestBody ScheduleEntryCommand command,
			Authentication authentication) {
		var scope = dataScopes.resolve(authentication.getName());
		dataScopes.assertScheduleEntryAccess(scope, id);
		dataScopes.assertOfferingAccess(scope, command.offeringId());
		dataScopes.assertClassroomAccess(scope, command.classroomId());
		return ok(service.saveEntry(id, command));
	}

	@DeleteMapping("/admin/education/schedules/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:delete','education:scheduling:manage')")
	public ResultData<Void> deleteEntry(
			@PathVariable String id,
			Authentication authentication) {
		dataScopes.assertScheduleEntryAccess(
				dataScopes.resolve(authentication.getName()),
				id);
		service.deleteEntry(id);
		return ok(null);
	}

	@GetMapping("/admin/education/teacher-time-constraints")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<List<TeacherTimeConstraint>> teacherConstraints(
			@RequestParam String semesterCode,
			Authentication authentication) {
		return ok(dataScopes.visibleTeacherConstraints(
				dataScopes.resolve(authentication.getName()),
				service.teacherConstraints(semesterCode)));
	}

	@PostMapping("/admin/education/teacher-time-constraints")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:create','education:scheduling:manage')")
	public ResultData<TeacherTimeConstraint> createTeacherConstraint(
			@RequestBody TeacherTimeConstraint command,
			Authentication authentication) {
		dataScopes.assertTeacherAccess(
				dataScopes.resolve(authentication.getName()),
				command.getTeacherId());
		return ok(service.saveTeacherConstraint(null, command));
	}

	@PutMapping("/admin/education/teacher-time-constraints/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:update','education:scheduling:manage')")
	public ResultData<TeacherTimeConstraint> updateTeacherConstraint(
			@PathVariable String id,
			@RequestBody TeacherTimeConstraint command,
			Authentication authentication) {
		var scope = dataScopes.resolve(authentication.getName());
		TeacherTimeConstraint current = service.teacherConstraint(id);
		dataScopes.assertTeacherAccess(scope, current.getTeacherId());
		dataScopes.assertTeacherAccess(scope, command.getTeacherId());
		return ok(service.saveTeacherConstraint(id, command));
	}

	@DeleteMapping("/admin/education/teacher-time-constraints/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:delete','education:scheduling:manage')")
	public ResultData<Void> deleteTeacherConstraint(
			@PathVariable String id,
			Authentication authentication) {
		TeacherTimeConstraint current = service.teacherConstraint(id);
		dataScopes.assertTeacherAccess(
				dataScopes.resolve(authentication.getName()),
				current.getTeacherId());
		service.deleteTeacherConstraint(id);
		return ok(null);
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}

	private void requireFullDataAccess(Authentication authentication) {
		dataScopes.assertFullAccess(dataScopes.resolve(authentication.getName()));
	}
}
