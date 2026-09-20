package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.ClassroomReservation;
import com.chronos.education.scheduling.model.ClassroomReservationView;
import com.chronos.education.scheduling.service.ClassroomReservationService;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClassroomReservationController {
	private final ClassroomReservationService service;
	private final EducationDataScopeService dataScopes;

	public ClassroomReservationController(
			ClassroomReservationService service,
			EducationDataScopeService dataScopes) {
		this.service = service;
		this.dataScopes = dataScopes;
	}

	@GetMapping("/portal/education/classrooms/options")
	@PreAuthorize("isAuthenticated()")
	public ResultData<List<Map<String, Object>>> classroomOptions(Principal principal) {
		return ok(service.classroomOptions(principal.getName()));
	}

	@GetMapping("/portal/education/academic-terms/options")
	@PreAuthorize("isAuthenticated()")
	public ResultData<List<Map<String, Object>>> termOptions() {
		return ok(service.termOptions());
	}

	@GetMapping("/portal/education/classroom-reservations")
	@PreAuthorize("isAuthenticated()")
	public ResultData<List<ClassroomReservationView>> mine(Principal principal) {
		return ok(service.mine(principal.getName()));
	}

	@PostMapping("/portal/education/classroom-reservations/{id}/cancel")
	@PreAuthorize("isAuthenticated()")
	public ResultData<ClassroomReservation> cancelMine(
			@PathVariable String id,
			@RequestBody Map<String, String> command,
			Principal principal) {
		return ok(service.cancel(principal.getName(), id, command.get("reason"), false));
	}

	@GetMapping("/admin/education/classroom-reservations")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:schedule:view')")
	public ResultData<List<ClassroomReservationView>> calendar(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			Principal principal) {
		dataScopes.assertFullAccess(dataScopes.resolve(principal.getName()));
		return ok(service.calendar(startDate, endDate));
	}

	@PostMapping("/admin/education/classroom-reservations/{id}/cancel")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:schedule:manage')")
	public ResultData<ClassroomReservation> cancelAsAdministrator(
			@PathVariable String id,
			@RequestBody Map<String, String> command,
			Principal principal) {
		dataScopes.assertFullAccess(dataScopes.resolve(principal.getName()));
		return ok(service.cancel(principal.getName(), id, command.get("reason"), true));
	}

	@PostMapping("/admin/education/classroom-reservations/{id}/retry")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:schedule:manage')")
	public ResultData<ClassroomReservation> retry(
			@PathVariable String id,
			Principal principal) {
		dataScopes.assertFullAccess(dataScopes.resolve(principal.getName()));
		return ok(service.retry(principal.getName(), id));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder()
				.code("200")
				.msg("ok")
				.data(data)
				.build();
	}
}
