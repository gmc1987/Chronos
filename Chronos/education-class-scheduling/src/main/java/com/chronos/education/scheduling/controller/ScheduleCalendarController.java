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
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.ScheduleOccurrenceService;

/** 管理端日期课表与课表例外接口。 */
@RestController
public class ScheduleCalendarController {
	private final ScheduleOccurrenceService service;
	private final EducationDataScopeService dataScopes;

	public ScheduleCalendarController(
			ScheduleOccurrenceService service,
			EducationDataScopeService dataScopes) {
		this.service = service;
		this.dataScopes = dataScopes;
	}

	@GetMapping("/admin/education/schedule-occurrences")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<?> occurrences(
			@RequestParam String semesterCode,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			Authentication authentication) {
		dataScopes.assertFullAccess(dataScopes.resolve(authentication.getName()));
		return ok(service.occurrences(semesterCode, date));
	}

	@GetMapping("/admin/education/schedule-date-exceptions")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<?> exceptions(
			@RequestParam String semesterCode,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			Authentication authentication) {
		dataScopes.assertFullAccess(dataScopes.resolve(authentication.getName()));
		return ok(service.exceptions(semesterCode, startDate, endDate));
	}

	@GetMapping("/admin/education/schedule-date-exceptions/history")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<?> history(
			@RequestParam String semesterCode,
			Authentication authentication) {
		dataScopes.assertFullAccess(dataScopes.resolve(authentication.getName()));
		return ok(service.exceptionHistory(semesterCode));
	}

	@PostMapping("/admin/education/schedule-date-exceptions")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:update','education:scheduling:manage')")
	public ResultData<?> create(
			@RequestBody ScheduleDateException command,
			Authentication authentication) {
		dataScopes.assertScheduleEntryAccess(
				dataScopes.resolve(authentication.getName()),
				command.getSourceEntryId());
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

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
