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
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.OfficialHolidayImportService;
import org.springframework.security.core.Authentication;

@RestController
public class AcademicCalendarController {
	private final AcademicCalendarService service;
	private final EducationDataScopeService dataScopes;
	private final OfficialHolidayImportService holidayImports;
	private final com.chronos.security.DirectoryDataScopeGuard directoryScope;

	public AcademicCalendarController(
			AcademicCalendarService service,
			EducationDataScopeService dataScopes,
			OfficialHolidayImportService holidayImports,
			com.chronos.security.DirectoryDataScopeGuard directoryScope) {
		this.service = service;
		this.dataScopes = dataScopes;
		this.holidayImports = holidayImports;
		this.directoryScope = directoryScope;
	}

	@GetMapping("/admin/education/term-progress")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:view','education:term:manage')")
	public ResultData<?> termProgress(
			@RequestParam String termCode,
			@RequestParam(required = false) LocalDate date,
			Authentication authentication) {
		dataScopes.assertTermAccess(dataScopes.resolve(authentication.getName()), termCode);
		return ok(service.termProgress(termCode, date));
	}

	@GetMapping("/admin/education/calendar-days")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:view','education:term:manage')")
	public ResultData<?> calendarDays(@RequestParam String termId,
			@RequestParam(required = false) String organizationId, Authentication authentication) {
		String owner = requireCalendarTerm(authentication, termId);
		if (organizationId != null && !organizationId.isBlank() && !organizationId.equals(owner)) {
			throw new IllegalArgumentException("学期与所选机构不一致");
		}
		return ok(service.calendarDays(termId));
	}

	@PostMapping("/admin/education/calendar-days")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:update','education:term:manage')")
	public ResultData<?> createCalendarDay(@RequestBody AcademicCalendarDay command, Authentication authentication) {
		requireCalendarTerm(authentication, command.getAcademicTermId());
		return ok(service.saveCalendarDay(null, command));
	}

	@PostMapping("/admin/education/calendar-days/import")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:update','education:term:manage')")
	public ResultData<?> importCalendarDays(@RequestBody CalendarImportCommand command, Authentication authentication) {
		String owner = requireCalendarTerm(authentication, command.termId());
		if (command.organizationId() == null || !command.organizationId().equals(owner)) {
			throw new IllegalArgumentException("请选择与学期一致的机构");
		}
		return ok(service.importCalendarDays(command.termId(), command.rows(), !Boolean.FALSE.equals(command.preview())));
	}

	public record CalendarImportCommand(
			String organizationId, String termId, Boolean preview, java.util.List<AcademicCalendarDay> rows) { }

	@PutMapping("/admin/education/calendar-days/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:update','education:term:manage')")
	public ResultData<?> updateCalendarDay(
			@PathVariable String id,
			@RequestBody AcademicCalendarDay command, Authentication authentication) {
		String originalTerm = service.calendarDayTermId(id);
		requireCalendarTerm(authentication, originalTerm);
		if (!originalTerm.equals(command.getAcademicTermId())) {
			throw new IllegalArgumentException("教学日历记录不能转移到其他学期");
		}
		return ok(service.saveCalendarDay(id, command));
	}

	@DeleteMapping("/admin/education/calendar-days/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:update','education:term:manage')")
	public ResultData<?> deleteCalendarDay(@PathVariable String id, Authentication authentication) {
		requireCalendarTerm(authentication, service.calendarDayTermId(id));
		service.deleteCalendarDay(id);
		return ok(null);
	}

	@PostMapping("/admin/education/holidays/import")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:update','education:term:manage')")
	public ResultData<?> importHolidays(
			@RequestBody HolidayImportCommand command,
			Authentication authentication) {
		dataScopes.assertFullAccess(dataScopes.resolve(authentication.getName()));
		return ok("MANUAL".equals(command.mode())
				? holidayImports.importManual(command.termId(), command.year(), command.content())
				: holidayImports.importOfficial(command.termId(), command.year()));
	}

	public record HolidayImportCommand(String termId, int year, String mode, String content) { }

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

	private String requireCalendarTerm(Authentication authentication, String termId) {
		String organizationId = service.termOrganization(termId);
		directoryScope.requireOrganization(authentication, organizationId);
		return organizationId;
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder()
				.code("200")
				.msg("success")
				.data(data)
				.build();
	}
}
