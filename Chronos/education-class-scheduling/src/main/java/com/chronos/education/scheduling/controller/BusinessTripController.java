package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.BusinessTripConfiguration;
import com.chronos.education.scheduling.model.BusinessTripRequest;
import com.chronos.education.scheduling.service.BusinessTripService;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class BusinessTripController {
	private final BusinessTripService service;
	private final EducationDataScopeService scopes;

	public BusinessTripController(BusinessTripService service, EducationDataScopeService scopes) {
		this.service = service;
		this.scopes = scopes;
	}

	@GetMapping("/portal/collaboration/business-trips")
	@PreAuthorize("isAuthenticated()")
	public ResultData<List<BusinessTripRequest>> mine(Principal principal) {
		return ok(service.mine(principal.getName()));
	}

	@PostMapping("/portal/collaboration/business-trips")
	@PreAuthorize("isAuthenticated()")
	public ResultData<BusinessTripRequest> start(
			@RequestBody Map<String, Object> command, Principal principal) {
		return ok(service.start(principal.getName(), command));
	}

	@PostMapping("/portal/collaboration/business-trips/{id}/withdraw")
	@PreAuthorize("isAuthenticated()")
	public ResultData<BusinessTripRequest> withdraw(
			@PathVariable String id,
			@RequestBody(required = false) Map<String, String> command,
			Principal principal) {
		return ok(service.withdraw(
				principal.getName(), id, command == null ? null : command.get("reason")));
	}

	@PostMapping("/portal/collaboration/business-trips/{id}/cancel")
	@PreAuthorize("isAuthenticated()")
	public ResultData<BusinessTripRequest> cancel(
			@PathVariable String id, @RequestBody Map<String, String> command, Principal principal) {
		return ok(service.cancel(principal.getName(), id, command.get("reason")));
	}

	@GetMapping("/portal/collaboration/business-trips/integration-status")
	@PreAuthorize("isAuthenticated()")
	public ResultData<Map<String, Object>> integrationStatus() {
		return ok(service.integrationStatus());
	}

	@GetMapping("/admin/collaboration/business-trip/config")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teacher:business:view','education:teacher:business:manage')")
	public ResultData<Map<String, Object>> configuration(Principal principal) {
		scopes.assertFullAccess(scopes.resolve(principal.getName()));
		return ok(service.integrationStatus());
	}

	@PutMapping("/admin/collaboration/business-trip/config")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:teacher:business:manage')")
	public ResultData<BusinessTripConfiguration> updateConfiguration(
			@RequestBody Map<String, Object> command, Principal principal) {
		scopes.assertFullAccess(scopes.resolve(principal.getName()));
		return ok(service.updateConfiguration(
				Boolean.TRUE.equals(command.get("financeRequired")), principal.getName()));
	}

	@GetMapping("/admin/collaboration/attendance/approved-business-trips")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teacher:business:view','education:teacher:business:manage')")
	public ResultData<List<BusinessTripRequest>> approvedForAttendance(
			@RequestParam String employeeId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			Principal principal) {
		scopes.assertFullAccess(scopes.resolve(principal.getName()));
		return ok(service.approvedForAttendance(employeeId, startDate, endDate));
	}

	private <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("ok").data(value).build();
	}
}
