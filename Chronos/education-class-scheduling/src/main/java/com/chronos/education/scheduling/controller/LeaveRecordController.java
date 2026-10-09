package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.LeaveRequestRecord;
import com.chronos.education.scheduling.model.StaffLeaveBalance;
import com.chronos.education.scheduling.model.LeaveBalanceAdjustment;
import com.chronos.education.scheduling.service.LeaveRecordService;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.model.workflow.WorkflowInstance;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
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

	@PostMapping("/portal/education/leaves")
	@PreAuthorize("isAuthenticated()")
	public ResultData<WorkflowInstance> start(
			@RequestBody Map<String, Object> command,
			Principal principal) {
		return ok(service.startLeave(principal.getName(), command));
	}

	@PostMapping("/portal/education/leaves/{id}/cancellation")
	@PreAuthorize("isAuthenticated()")
	public ResultData<LeaveRequestRecord> requestCancellation(
			@PathVariable String id,
			@RequestBody Map<String, String> command,
			Principal principal) {
		return ok(service.requestCancellation(
				principal.getName(),
				id,
				command.get("reason"),
				command.get("actualEndDate") == null || command.get("actualEndDate").isBlank()
						? null : LocalDate.parse(command.get("actualEndDate"))));
	}

	@PostMapping("/portal/education/leaves/{id}/withdraw")
	@PreAuthorize("isAuthenticated()")
	public ResultData<LeaveRequestRecord> withdraw(
			@PathVariable String id,
			@RequestBody(required = false) Map<String, String> command,
			Principal principal) {
		return ok(service.withdraw(
				principal.getName(), id, command == null ? null : command.get("reason")));
	}

	@GetMapping("/portal/education/leave-balances")
	@PreAuthorize("isAuthenticated()")
	public ResultData<List<StaffLeaveBalance>> myBalances(
			@RequestParam(required = false) Integer year,
			Principal principal) {
		return ok(service.myBalances(
				principal.getName(), year == null ? java.time.Year.now().getValue() : year));
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

	@PostMapping("/admin/education/leave-balances/{employeeId}/adjust")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teacher:business:manage','education:student:manage')")
	public ResultData<StaffLeaveBalance> adjustBalance(
			@PathVariable String employeeId,
			@RequestBody Map<String, Object> command,
			Principal principal) {
		scopes.assertFullAccess(scopes.resolve(principal.getName()));
		return ok(service.adjustBalance(
				employeeId,
				Integer.parseInt(String.valueOf(command.get("year"))),
				String.valueOf(command.get("leaveType")),
				new BigDecimal(String.valueOf(command.get("changeDays"))),
				String.valueOf(command.get("reason")),
				principal.getName()));
	}

	@GetMapping("/admin/education/leave-balances/{employeeId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teacher:business:view','education:student:view')")
	public ResultData<List<StaffLeaveBalance>> staffBalances(
			@PathVariable String employeeId,
			@RequestParam int year,
			Principal principal) {
		scopes.assertFullAccess(scopes.resolve(principal.getName()));
		return ok(service.staffBalances(employeeId, year));
	}

	@GetMapping("/admin/education/leave-balances/{employeeId}/adjustments")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teacher:business:view','education:student:view')")
	public ResultData<List<LeaveBalanceAdjustment>> balanceAdjustments(
			@PathVariable String employeeId,
			@RequestParam int year,
			Principal principal) {
		scopes.assertFullAccess(scopes.resolve(principal.getName()));
		return ok(service.balanceAdjustments(employeeId, year));
	}

	@GetMapping("/admin/education/attendance/approved-leaves")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teacher:business:view','education:student:view')")
	public ResultData<List<LeaveRequestRecord>> approvedLeaves(
			@RequestParam String employeeId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			Principal principal) {
		scopes.assertFullAccess(scopes.resolve(principal.getName()));
		return ok(service.approvedStaffLeaves(employeeId, startDate, endDate));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
