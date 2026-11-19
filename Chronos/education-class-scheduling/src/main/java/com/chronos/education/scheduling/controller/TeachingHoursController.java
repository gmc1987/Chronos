package com.chronos.education.scheduling.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.service.TeachingHoursService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/education/teaching-hours")
@RequiredArgsConstructor
public class TeachingHoursController {
	private final TeachingHoursService service;

	@GetMapping
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<List<TeachingHoursService.Row>> list(
			@RequestParam String organizationId, @RequestParam String semesterCode,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(required = false) String teacherId, @RequestParam(required = false) String courseCode,
			@RequestParam(required = false) String offeringId, Principal actor) {
		return ok(service.list(new TeachingHoursService.Query(organizationId, semesterCode,
				startDate, endDate, teacherId, courseCode, offeringId), actor.getName()));
	}

	@GetMapping("/history")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<List<TeachingHoursService.Revision>> history(@RequestParam String organizationId,
			@RequestParam String semesterCode, @RequestParam String key, Principal actor) {
		return ok(service.history(organizationId, semesterCode, key, actor.getName()));
	}

	@PostMapping("/confirmations")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:scheduling:manage')")
	public ResultData<Integer> confirm(@RequestBody TeachingHoursService.Confirmation command, Principal actor) {
		return ok(service.confirm(command, actor.getName()));
	}

	private <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("ok").data(value).build();
	}
}
