package com.chronos.education.scheduling.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.AcademicCalendarDay;
import com.chronos.education.scheduling.model.BellPeriod;
import com.chronos.education.scheduling.model.BellSchedule;
import com.chronos.education.scheduling.service.AcademicCalendarService;

@RestController
public class AcademicCalendarController {
	private final AcademicCalendarService service;

	public AcademicCalendarController(AcademicCalendarService service) {
		this.service = service;
	}

	@GetMapping("/admin/education/term-progress")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:view','education:term:manage')")
	public ResultData<?> termProgress(
			@RequestParam String termCode,
			@RequestParam(required = false) LocalDate date) {
		return ok(service.termProgress(termCode, date));
	}

	@GetMapping("/admin/education/calendar-days")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:view','education:term:manage')")
	public ResultData<?> calendarDays(@RequestParam String termId) {
		return ok(service.calendarDays(termId));
	}

	@PostMapping("/admin/education/calendar-days")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:update','education:term:manage')")
	public ResultData<?> createCalendarDay(@RequestBody AcademicCalendarDay command) {
		return ok(service.saveCalendarDay(null, command));
	}

	@PutMapping("/admin/education/calendar-days/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:update','education:term:manage')")
	public ResultData<?> updateCalendarDay(
			@PathVariable String id,
			@RequestBody AcademicCalendarDay command) {
		return ok(service.saveCalendarDay(id, command));
	}

	@DeleteMapping("/admin/education/calendar-days/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:update','education:term:manage')")
	public ResultData<?> deleteCalendarDay(@PathVariable String id) {
		service.deleteCalendarDay(id);
		return ok(null);
	}

	@GetMapping("/admin/education/bell-schedules")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:view','education:term:manage')")
	public ResultData<?> bellSchedules(@RequestParam String termId) {
		return ok(service.bellSchedules(termId));
	}

	@PostMapping("/admin/education/bell-schedules")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:update','education:term:manage')")
	public ResultData<?> createBellSchedule(@RequestBody BellSchedule command) {
		return ok(service.saveBellSchedule(null, command));
	}

	@PutMapping("/admin/education/bell-schedules/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:update','education:term:manage')")
	public ResultData<?> updateBellSchedule(
			@PathVariable String id,
			@RequestBody BellSchedule command) {
		return ok(service.saveBellSchedule(id, command));
	}

	@DeleteMapping("/admin/education/bell-schedules/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:update','education:term:manage')")
	public ResultData<?> deleteBellSchedule(@PathVariable String id) {
		service.deleteBellSchedule(id);
		return ok(null);
	}

	@PostMapping("/admin/education/bell-periods")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:update','education:term:manage')")
	public ResultData<?> createBellPeriod(@RequestBody BellPeriod command) {
		return ok(service.saveBellPeriod(null, command));
	}

	@PutMapping("/admin/education/bell-periods/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:update','education:term:manage')")
	public ResultData<?> updateBellPeriod(
			@PathVariable String id,
			@RequestBody BellPeriod command) {
		return ok(service.saveBellPeriod(id, command));
	}

	@DeleteMapping("/admin/education/bell-periods/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:update','education:term:manage')")
	public ResultData<?> deleteBellPeriod(@PathVariable String id) {
		service.deleteBellPeriod(id);
		return ok(null);
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder()
				.code("200")
				.msg("success")
				.data(data)
				.build();
	}
}
