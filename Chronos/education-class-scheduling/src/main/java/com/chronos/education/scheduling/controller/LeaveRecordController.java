package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.LeaveRequestRecord;
import com.chronos.education.scheduling.service.LeaveRecordService;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class LeaveRecordController {
	private final LeaveRecordService service;
	private final EducationDataScopeService scopes;
	public LeaveRecordController(
			LeaveRecordService service,
			EducationDataScopeService scopes) {
		this.service = service;
		this.scopes = scopes;
	}

	@GetMapping("/portal/education/leaves")
	@PreAuthorize("isAuthenticated()")
	public ResultData<List<LeaveRequestRecord>> mine(Principal principal) {
		return ok(service.mine(principal.getName()));
	}

	@PostMapping("/portal/education/leaves/{id}/cancellation")
	@PreAuthorize("isAuthenticated()")
	public ResultData<LeaveRequestRecord> requestCancellation(
			@PathVariable String id,
			@RequestBody Map<String, String> command,
			Principal principal) {
		return ok(service.requestCancellation(
				principal.getName(), id, command.get("reason")));
	}

	@GetMapping("/admin/education/leaves/cancellations")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teacher:business:manage','education:student:manage')")
	public ResultData<List<LeaveRequestRecord>> pendingCancellations(Principal principal) {
		scopes.assertFullAccess(scopes.resolve(principal.getName()));
		return ok(service.pendingCancellations());
	}

	@PostMapping("/admin/education/leaves/{id}/cancellation-decision")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teacher:business:manage','education:student:manage')")
	public ResultData<LeaveRequestRecord> decide(
			@PathVariable String id,
			@RequestBody Map<String, Object> command,
			Principal principal) {
		scopes.assertFullAccess(scopes.resolve(principal.getName()));
		return ok(service.decideCancellation(
				principal.getName(),
				id,
				Boolean.TRUE.equals(command.get("approved")),
				command.get("comment") == null ? null : String.valueOf(command.get("comment"))));
	}

	@GetMapping("/admin/education/leaves/statistics")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teacher:business:view','education:student:view')")
	public ResultData<Map<String, Object>> statistics(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			Principal principal) {
		scopes.assertFullAccess(scopes.resolve(principal.getName()));
		return ok(service.statistics(startDate, endDate));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
