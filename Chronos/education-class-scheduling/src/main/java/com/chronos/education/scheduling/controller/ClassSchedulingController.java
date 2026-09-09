package com.chronos.education.scheduling.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleEntryCommand;
import com.chronos.education.scheduling.model.ScheduleEntryView;
import com.chronos.education.scheduling.model.SchedulePlanVersionView;
import com.chronos.education.scheduling.model.TeacherTimeConstraint;
import com.chronos.education.scheduling.service.ClassSchedulingService;
import com.chronos.education.scheduling.service.SchedulePlanVersionService;

@RestController
public class ClassSchedulingController {
	private final ClassSchedulingService service;
	private final SchedulePlanVersionService planVersions;

	public ClassSchedulingController(
			ClassSchedulingService service,
			SchedulePlanVersionService planVersions) {
		this.service = service;
		this.planVersions = planVersions;
	}

	@GetMapping("/admin/education/schedule-versions")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<List<SchedulePlanVersionView>> scheduleVersions(@RequestParam String semesterCode) {
		return ok(planVersions.versions(semesterCode));
	}

	@PostMapping("/admin/education/schedule-versions/publish")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:scheduling:manage')")
	public ResultData<SchedulePlanVersionView> publishSchedule(
			@RequestParam String semesterCode,
			java.security.Principal principal) {
		return ok(SchedulePlanVersionView.from(planVersions.publish(semesterCode, principal.getName())));
	}

	@PostMapping("/admin/education/schedule-versions/{id}/rollback")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:scheduling:manage')")
	public ResultData<SchedulePlanVersionView> rollbackSchedule(
			@PathVariable String id,
			java.security.Principal principal) {
		return ok(SchedulePlanVersionView.from(planVersions.rollback(id, principal.getName())));
	}

	@GetMapping("/admin/education/course-offerings")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<List<CourseOffering>> offerings(@RequestParam String semesterCode) {
		return ok(service.offerings(semesterCode));
	}

	@PostMapping("/admin/education/course-offerings")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:create','education:scheduling:manage')")
	public ResultData<CourseOffering> createOffering(@RequestBody CourseOffering command) {
		return ok(service.saveOffering(null, command));
	}

	@PutMapping("/admin/education/course-offerings/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:update','education:scheduling:manage')")
	public ResultData<CourseOffering> updateOffering(
			@PathVariable String id,
			@RequestBody CourseOffering command) {
		return ok(service.saveOffering(id, command));
	}

	@DeleteMapping("/admin/education/course-offerings/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:delete','education:scheduling:manage')")
	public ResultData<Void> deleteOffering(@PathVariable String id) {
		service.deleteOffering(id);
		return ok(null);
	}

	@GetMapping("/admin/education/classrooms")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:resource:venue:view','education:resource:venue:manage')")
	public ResultData<List<Classroom>> classrooms() {
		return ok(service.classrooms());
	}

	@PostMapping("/admin/education/classrooms")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:resource:venue:create','education:resource:venue:manage')")
	public ResultData<Classroom> createClassroom(@RequestBody Classroom command) {
		return ok(service.saveClassroom(null, command));
	}

	@PutMapping("/admin/education/classrooms/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:resource:venue:update','education:resource:venue:manage')")
	public ResultData<Classroom> updateClassroom(
			@PathVariable String id,
			@RequestBody Classroom command) {
		return ok(service.saveClassroom(id, command));
	}

	@DeleteMapping("/admin/education/classrooms/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:resource:venue:delete','education:resource:venue:manage')")
	public ResultData<Void> deleteClassroom(@PathVariable String id) {
		service.deleteClassroom(id);
		return ok(null);
	}

	@GetMapping("/admin/education/schedules")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<List<ScheduleEntryView>> schedule(
			@RequestParam String semesterCode,
			@RequestParam(defaultValue = "ALL") String dimension,
			@RequestParam(required = false) String targetId) {
		return ok(service.schedule(semesterCode, dimension, targetId));
	}

	@PostMapping("/admin/education/schedules")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:create','education:scheduling:manage')")
	public ResultData<ScheduleEntryView> createEntry(@RequestBody ScheduleEntryCommand command) {
		return ok(service.saveEntry(null, command));
	}

	@PutMapping("/admin/education/schedules/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:update','education:scheduling:manage')")
	public ResultData<ScheduleEntryView> updateEntry(
			@PathVariable String id,
			@RequestBody ScheduleEntryCommand command) {
		return ok(service.saveEntry(id, command));
	}

	@DeleteMapping("/admin/education/schedules/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:delete','education:scheduling:manage')")
	public ResultData<Void> deleteEntry(@PathVariable String id) {
		service.deleteEntry(id);
		return ok(null);
	}

	@GetMapping("/admin/education/teacher-time-constraints")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<List<TeacherTimeConstraint>> teacherConstraints(@RequestParam String semesterCode) {
		return ok(service.teacherConstraints(semesterCode));
	}

	@PostMapping("/admin/education/teacher-time-constraints")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:create','education:scheduling:manage')")
	public ResultData<TeacherTimeConstraint> createTeacherConstraint(
			@RequestBody TeacherTimeConstraint command) {
		return ok(service.saveTeacherConstraint(null, command));
	}

	@PutMapping("/admin/education/teacher-time-constraints/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:update','education:scheduling:manage')")
	public ResultData<TeacherTimeConstraint> updateTeacherConstraint(
			@PathVariable String id,
			@RequestBody TeacherTimeConstraint command) {
		return ok(service.saveTeacherConstraint(id, command));
	}

	@DeleteMapping("/admin/education/teacher-time-constraints/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:delete','education:scheduling:manage')")
	public ResultData<Void> deleteTeacherConstraint(@PathVariable String id) {
		service.deleteTeacherConstraint(id);
		return ok(null);
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
