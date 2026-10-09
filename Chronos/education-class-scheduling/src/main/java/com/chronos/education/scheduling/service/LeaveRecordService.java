package com.chronos.education.scheduling.service;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.LeaveRequestRecordRepository;
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.education.scheduling.model.LeaveBalanceAdjustment;
import com.chronos.education.scheduling.model.LeaveRequestRecord;
import com.chronos.education.scheduling.model.StaffLeaveBalance;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Employee;
import com.chronos.model.workflow.WorkflowInstance;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.workflow.WorkflowService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 请假申请、额度、撤回、销假和考勤数据的统一业务服务。 */
@Service
public class LeaveRecordService {
	private final LeaveRequestRecordRepository records;
	private final EducationUserBindingRepository bindings;
	private final EducationApplicantResolver applicants;
	private final WorkflowService workflows;
	private final IAuditLogService audit;
	private final IAdminUserRepository users;
	private final IEmployeeRepository employees;
	private final StaffLeaveBalanceService leaveBalances;

	public LeaveRecordService(
			LeaveRequestRecordRepository records,
			EducationUserBindingRepository bindings,
			EducationApplicantResolver applicants,
			WorkflowService workflows,
			IAuditLogService audit,
			IAdminUserRepository users,
			IEmployeeRepository employees,
			StaffLeaveBalanceService leaveBalances) {
		this.records = records;
		this.bindings = bindings;
		this.applicants = applicants;
		this.workflows = workflows;
		this.audit = audit;
		this.users = users;
		this.employees = employees;
		this.leaveBalances = leaveBalances;
	}

	@Transactional
	public WorkflowInstance startLeave(String username, Map<String, Object> command) {
		String leaveType = required(text(command, "leaveType"), "请假类型不能为空");
		LocalDate start = LocalDate.parse(required(text(command, "startDate"), "开始日期不能为空"));
		LocalDate end = LocalDate.parse(required(text(command, "endDate"), "结束日期不能为空"));
		if (end.isBefore(start)) throw new IllegalArgumentException("结束日期不能早于开始日期");
		String reason = required(text(command, "reason"), "请假原因不能为空");
		LeaveIdentity leaveIdentity = resolveApplicant(username, text(command, "studentId"));
		if ("STAFF".equals(leaveIdentity.type())) {
			leaveBalances.assertAvailable(leaveIdentity.id(), leaveType, start, end);
		}

		Map<String, Object> form = new LinkedHashMap<>();
		form.put("leaveType", leaveType);
		form.put("startDate", start.toString());
		form.put("endDate", end.toString());
		form.put("reason", reason);
		form.put("applicantType", leaveIdentity.type());
		if ("STUDENT".equals(leaveIdentity.type())) form.put("studentId", leaveIdentity.id());
		String flowCode = "STUDENT".equals(leaveIdentity.type())
				? "EDU_STUDENT_LEAVE_APPROVAL" : "EDU_TEACHER_LEAVE_APPROVAL";
		WorkflowInstance instance = workflows.startByCode(
				flowCode, "EDU_LEAVE:" + UUID.randomUUID(), form, username);

		LeaveRequestRecord record = new LeaveRequestRecord();
		record.setWorkflowInstanceId(instance.getId());
		record.setBusinessKey(instance.getBusinessKey());
		record.setApplicantType(leaveIdentity.type());
		record.setApplicantId(leaveIdentity.id());
		record.setLeaveType(leaveType);
		record.setStartDate(start);
		record.setEndDate(end);
		record.setRequestedDays(leaveBalances.requestedDays(start, end));
		record.setReason(reason);
		record.setStatus("PENDING");
		records.save(record);
		audit.log(username, "EDUCATION_LEAVE_SUBMIT",
				"workflowInstanceId=" + instance.getId() + ",applicantType=" + leaveIdentity.type());
		return instance;
	}

	@Transactional(readOnly = true)
	public List<LeaveRequestRecord> mine(String username) {
		LeaveIdentity identity = leaveIdentity(username);
		return records.findByApplicantTypeAndApplicantIdOrderByStartDateDesc(identity.type(), identity.id());
	}

	@Transactional
	public LeaveRequestRecord withdraw(String username, String id, String reason) {
		LeaveRequestRecord record = records.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("请假记录不存在"));
		assertOwner(username, record);
		if (!"PENDING".equals(record.getStatus())) {
			throw new IllegalStateException("只有审批中的请假可以撤回");
		}
		workflows.withdraw(record.getWorkflowInstanceId(), reason, username);
		record.setStatus("WITHDRAWN");
		record.setWithdrawnBy(username);
		record.setWithdrawnAt(LocalDateTime.now());
		audit.log(username, "EDUCATION_LEAVE_WITHDRAW", "leaveId=" + id);
		return records.save(record);
	}

	@Transactional
	public LeaveRequestRecord requestCancellation(
			String username, String id, String reason, LocalDate actualEndDate) {
		LeaveRequestRecord record = records.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("请假记录不存在"));
		assertOwner(username, record);
		if (!"APPROVED".equals(record.getStatus())
				|| !List.of("NONE", "REJECTED").contains(record.getCancellationStatus())) {
			throw new IllegalStateException("当前请假记录不能申请销假");
		}
		LocalDate effectiveEnd = actualEndDate == null ? record.getStartDate().minusDays(1) : actualEndDate;
		if (effectiveEnd.isBefore(record.getStartDate().minusDays(1))
				|| !effectiveEnd.isBefore(record.getEndDate())) {
			throw new IllegalArgumentException("实际结束日期必须早于原请假结束日期");
		}
		record.setCancellationStatus("PENDING");
		record.setCancellationReason(required(reason, "销假原因不能为空"));
		record.setActualEndDate(effectiveEnd);
		record.setCancellationRequestedBy(username);
		record.setCancellationRequestedAt(LocalDateTime.now());
		audit.log(username, "EDUCATION_LEAVE_CANCELLATION_REQUEST", "leaveId=" + id);
		return records.save(record);
	}

	@Transactional(readOnly = true)
	public List<LeaveRequestRecord> pendingCancellations() {
		return records.findByCancellationStatusOrderByCancellationRequestedAtAsc("PENDING");
	}

	@Transactional
	public LeaveRequestRecord decideCancellation(
			String username, String id, boolean approved, String comment) {
		LeaveRequestRecord record = records.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("请假记录不存在"));
		if (!"PENDING".equals(record.getCancellationStatus())) {
			throw new IllegalStateException("销假申请已处理");
		}
		record.setCancellationStatus(approved ? "APPROVED" : "REJECTED");
		record.setCancellationDecidedBy(username);
		record.setCancellationDecidedAt(LocalDateTime.now());
		record.setCancellationComment(comment == null ? null : comment.trim());
		if (approved) {
			BigDecimal actualDays = record.getActualEndDate().isBefore(record.getStartDate())
					? BigDecimal.ZERO
					: leaveBalances.requestedDays(record.getStartDate(), record.getActualEndDate());
			leaveBalances.refund(
					record, record.getRequestedDays().subtract(actualDays), username, "销假审批通过返还");
			record.setStatus(actualDays.signum() == 0 ? "CANCELLED" : "PARTIALLY_CANCELLED");
		}
		audit.log(username, "EDUCATION_LEAVE_CANCELLATION_DECIDE",
				"leaveId=" + id + ",approved=" + approved);
		return records.save(record);
	}

	@Transactional
	public List<StaffLeaveBalance> myBalances(String username, int year) {
		return leaveBalances.balances(requireStaffIdentity(username).id(), year);
	}

	@Transactional
	public StaffLeaveBalance adjustBalance(
			String employeeId, int year, String leaveType, BigDecimal changeDays,
			String reason, String username) {
		requireActiveEmployee(employeeId);
		return leaveBalances.adjust(employeeId, year, leaveType, changeDays, reason, username);
	}

	@Transactional
	public List<StaffLeaveBalance> staffBalances(String employeeId, int year) {
		requireActiveEmployee(employeeId);
		return leaveBalances.balances(employeeId, year);
	}

	@Transactional(readOnly = true)
	public List<LeaveBalanceAdjustment> balanceAdjustments(String employeeId, int year) {
		requireActiveEmployee(employeeId);
		return leaveBalances.adjustments(employeeId, year);
	}

	@Transactional(readOnly = true)
	public List<LeaveRequestRecord> approvedStaffLeaves(
			String employeeId, LocalDate start, LocalDate end) {
		if (start == null || end == null || end.isBefore(start)) {
			throw new IllegalArgumentException("查询日期范围不正确");
		}
		return records
				.findByApplicantTypeAndApplicantIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
						"STAFF", employeeId, List.of("APPROVED", "PARTIALLY_CANCELLED"), end, start)
				.stream()
				.filter(item -> !effectiveEnd(item).isBefore(start))
				.toList();
	}

	@Transactional(readOnly = true)
	public Map<String, Object> statistics(LocalDate start, LocalDate end) {
		if (start == null || end == null || end.isBefore(start)) {
			throw new IllegalArgumentException("统计日期范围不正确");
		}
		List<LeaveRequestRecord> values =
				records.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(end, start);
		Map<String, Long> byType = values.stream().collect(java.util.stream.Collectors.groupingBy(
				LeaveRequestRecord::getLeaveType, java.util.stream.Collectors.counting()));
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("requestCount", values.size());
		result.put("staffCount", values.stream()
				.filter(item -> "STAFF".equals(item.getApplicantType())).count());
		result.put("teacherCount", values.stream()
				.filter(item -> "TEACHER".equals(item.getApplicantType())).count());
		result.put("studentCount", values.stream()
				.filter(item -> "STUDENT".equals(item.getApplicantType())).count());
		result.put("cancelledCount", values.stream()
				.filter(item -> "CANCELLED".equals(item.getStatus())).count());
		result.put("leaveDays", values.stream()
				.filter(item -> !"WITHDRAWN".equals(item.getStatus()) && !"REJECTED".equals(item.getStatus()))
				.mapToLong(item -> ChronoUnit.DAYS.between(
						item.getStartDate().isBefore(start) ? start : item.getStartDate(),
						(effectiveEnd(item).isAfter(end) ? end : effectiveEnd(item)).plusDays(1)))
				.filter(days -> days > 0)
				.sum());
		result.put("byType", byType);
		return result;
	}

	private LocalDate effectiveEnd(LeaveRequestRecord record) {
		return "APPROVED".equals(record.getCancellationStatus()) && record.getActualEndDate() != null
				? record.getActualEndDate()
				: record.getEndDate();
	}

	private LeaveIdentity resolveApplicant(String username, String requestedStudentId) {
		LeaveIdentity staff = staffIdentity(username);
		if (staff != null) return staff;
		EducationUserBinding identity = bindings.findByUsernameAndStatusOrderByProfileType(username, "ACTIVE")
				.stream()
				.filter(value -> "TEACHER".equals(value.getProfileType())
						|| "STUDENT".equals(value.getProfileType()))
				.findFirst().orElse(null);
		if (identity != null) {
			String studentId = "STUDENT".equals(identity.getProfileType()) ? identity.getProfileId() : null;
			applicants.resolve(username, identity.getProfileType(), studentId);
			return new LeaveIdentity(identity.getProfileType(), identity.getProfileId());
		}
		bindings.findByUsernameAndProfileTypeAndStatus(username, "PARENT", "ACTIVE")
				.orElseThrow(() -> new AccessDeniedException("当前账号未绑定教职工、教师、学生或家长档案"));
		return new LeaveIdentity(
				"STUDENT", applicants.resolve(username, "STUDENT", requestedStudentId));
	}

	private LeaveIdentity leaveIdentity(String username) {
		LeaveIdentity staff = staffIdentity(username);
		if (staff != null) return staff;
		return bindings.findByUsernameAndStatusOrderByProfileType(username, "ACTIVE").stream()
				.filter(item -> "TEACHER".equals(item.getProfileType())
						|| "STUDENT".equals(item.getProfileType()))
				.map(item -> new LeaveIdentity(item.getProfileType(), item.getProfileId()))
				.findFirst()
				.orElseThrow(() -> new AccessDeniedException("当前账号未绑定有效人员档案"));
	}

	private LeaveIdentity requireStaffIdentity(String username) {
		LeaveIdentity identity = staffIdentity(username);
		if (identity == null) throw new AccessDeniedException("当前账号未绑定有效教职工档案");
		return identity;
	}

	private LeaveIdentity staffIdentity(String username) {
		AdminUser account = users.findByUsername(username);
		if (account == null || account.getEmployeeId() == null || account.getEmployeeId().isBlank()) return null;
		return employees.findById(account.getEmployeeId())
				.filter(employee -> "ACTIVE".equals(employee.getEmploymentStatus()))
				.map(employee -> new LeaveIdentity("STAFF", employee.getId()))
				.orElse(null);
	}

	private Employee requireActiveEmployee(String employeeId) {
		return employees.findById(employeeId)
				.filter(employee -> "ACTIVE".equals(employee.getEmploymentStatus()))
				.orElseThrow(() -> new IllegalArgumentException("有效教职工档案不存在"));
	}

	private void assertOwner(String username, LeaveRequestRecord record) {
		LeaveIdentity identity = leaveIdentity(username);
		if (!identity.type().equals(record.getApplicantType()) || !identity.id().equals(record.getApplicantId())) {
			throw new AccessDeniedException("只能操作本人的请假记录");
		}
	}

	private String required(String value, String message) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
		return value.trim();
	}

	private String text(Map<String, Object> values, String key) {
		Object value = values == null ? null : values.get(key);
		return value == null ? null : String.valueOf(value);
	}

	private record LeaveIdentity(String type, String id) {}
}
