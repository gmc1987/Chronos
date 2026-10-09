package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.LeaveBalanceAdjustmentRepository;
import com.chronos.education.scheduling.dao.StaffLeaveBalanceRepository;
import com.chronos.education.scheduling.dao.LeaveRequestRecordRepository;
import com.chronos.education.scheduling.model.LeaveBalanceAdjustment;
import com.chronos.education.scheduling.model.LeaveRequestRecord;
import com.chronos.education.scheduling.model.StaffLeaveBalance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 教职工假期额度初始化、校验、扣减、返还和调整流水。 */
@Service
public class StaffLeaveBalanceService {
	private static final Map<String, BigDecimal> DEFAULT_ENTITLEMENTS;
	static {
		Map<String, BigDecimal> values = new LinkedHashMap<>();
		values.put("ANNUAL", new BigDecimal("10"));
		values.put("SICK", new BigDecimal("30"));
		values.put("PERSONAL", new BigDecimal("10"));
		values.put("MARRIAGE", new BigDecimal("3"));
		values.put("MATERNITY", new BigDecimal("98"));
		values.put("PATERNITY", new BigDecimal("15"));
		values.put("COMPENSATORY", BigDecimal.ZERO);
		DEFAULT_ENTITLEMENTS = Map.copyOf(values);
	}

	private final StaffLeaveBalanceRepository balances;
	private final LeaveBalanceAdjustmentRepository adjustments;
	private final LeaveRequestRecordRepository leaveRequests;

	public StaffLeaveBalanceService(
			StaffLeaveBalanceRepository balances,
			LeaveBalanceAdjustmentRepository adjustments,
			LeaveRequestRecordRepository leaveRequests) {
		this.balances = balances;
		this.adjustments = adjustments;
		this.leaveRequests = leaveRequests;
	}

	public boolean tracks(String leaveType) {
		return DEFAULT_ENTITLEMENTS.containsKey(leaveType);
	}

	public BigDecimal requestedDays(LocalDate start, LocalDate end) {
		return BigDecimal.valueOf(ChronoUnit.DAYS.between(start, end) + 1);
	}

	@Transactional
	public List<StaffLeaveBalance> balances(String employeeId, int year) {
		DEFAULT_ENTITLEMENTS.forEach((type, entitlement) -> ensure(employeeId, year, type, entitlement));
		return balances.findByEmployeeIdAndLeaveYearOrderByLeaveType(employeeId, year);
	}

	@Transactional
	public void assertAvailable(String employeeId, String leaveType, LocalDate start, LocalDate end) {
		if (!tracks(leaveType)) return;
		if (start.getYear() != end.getYear()) {
			throw new IllegalArgumentException("跨年度请假请按自然年度分开申请");
		}
		StaffLeaveBalance balance = locked(employeeId, start.getYear(), leaveType);
		BigDecimal days = requestedDays(start, end);
		BigDecimal reserved = leaveRequests
				.findByApplicantTypeAndApplicantIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
						"STAFF", employeeId, List.of("PENDING"),
						LocalDate.of(start.getYear(), 12, 31),
						LocalDate.of(start.getYear(), 1, 1))
				.stream()
				.filter(item -> leaveType.equals(item.getLeaveType()))
				.map(LeaveRequestRecord::getRequestedDays)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal available = balance.getAvailableDays().subtract(reserved);
		if (available.compareTo(days) < 0) {
			throw new IllegalStateException(
					"假期余额不足，可用 " + available.stripTrailingZeros().toPlainString()
							+ " 天，申请 " + days.stripTrailingZeros().toPlainString() + " 天");
		}
	}

	@Transactional
	public void deduct(LeaveRequestRecord record, String operator) {
		if (!"STAFF".equals(record.getApplicantType())
				|| !tracks(record.getLeaveType())
				|| Boolean.TRUE.equals(record.getBalanceDeducted())) {
			return;
		}
		if (record.getStartDate().getYear() != record.getEndDate().getYear()) {
			throw new IllegalStateException("跨年度请假必须按自然年度分开申请");
		}
		StaffLeaveBalance balance = locked(
				record.getApplicantId(), record.getStartDate().getYear(), record.getLeaveType());
		if (balance.getAvailableDays().compareTo(record.getRequestedDays()) < 0) {
			throw new IllegalStateException("审批通过时假期余额不足，请调整额度后重试");
		}
		balance.setConsumedDays(balance.getConsumedDays().add(record.getRequestedDays()));
		balances.save(balance);
		record.setBalanceDeducted(true);
		append(record.getApplicantId(), record.getStartDate().getYear(), record.getLeaveType(),
				record.getRequestedDays().negate(), "请假审批通过扣减", "LEAVE_APPROVED",
				record.getId(), operator);
	}

	@Transactional
	public void refund(LeaveRequestRecord record, BigDecimal days, String operator, String reason) {
		if (!"STAFF".equals(record.getApplicantType())
				|| !tracks(record.getLeaveType())
				|| !Boolean.TRUE.equals(record.getBalanceDeducted())
				|| days.signum() <= 0) {
			return;
		}
		StaffLeaveBalance balance = locked(
				record.getApplicantId(), record.getStartDate().getYear(), record.getLeaveType());
		balance.setConsumedDays(balance.getConsumedDays().subtract(days).max(BigDecimal.ZERO));
		balances.save(balance);
		append(record.getApplicantId(), record.getStartDate().getYear(), record.getLeaveType(),
				days, reason, "LEAVE_RETURN", record.getId(), operator);
	}

	@Transactional
	public StaffLeaveBalance adjust(
			String employeeId,
			int year,
			String leaveType,
			BigDecimal changeDays,
			String reason,
			String operator) {
		if (!tracks(leaveType)) throw new IllegalArgumentException("该假别不按余额管理");
		if (changeDays == null || changeDays.signum() == 0) {
			throw new IllegalArgumentException("调整天数不能为 0");
		}
		if (reason == null || reason.isBlank()) throw new IllegalArgumentException("调整原因不能为空");
		StaffLeaveBalance balance = locked(employeeId, year, leaveType);
		if (balance.getAvailableDays().add(changeDays).signum() < 0) {
			throw new IllegalArgumentException("调整后可用余额不能小于 0");
		}
		balance.setAdjustmentDays(balance.getAdjustmentDays().add(changeDays));
		balances.save(balance);
		append(employeeId, year, leaveType, changeDays, reason.trim(),
				"MANUAL_ADJUSTMENT", balance.getId(), operator);
		return balance;
	}

	@Transactional(readOnly = true)
	public List<LeaveBalanceAdjustment> adjustments(String employeeId, int year) {
		return adjustments.findByEmployeeIdAndLeaveYearOrderByCreateTimeDesc(employeeId, year);
	}

	private StaffLeaveBalance locked(String employeeId, int year, String leaveType) {
		ensure(employeeId, year, leaveType, DEFAULT_ENTITLEMENTS.getOrDefault(leaveType, BigDecimal.ZERO));
		return balances.findLocked(employeeId, year, leaveType).orElseThrow();
	}

	private StaffLeaveBalance ensure(
			String employeeId, int year, String leaveType, BigDecimal entitlement) {
		return balances.findByEmployeeIdAndLeaveYearAndLeaveType(employeeId, year, leaveType)
				.orElseGet(() -> {
					StaffLeaveBalance value = new StaffLeaveBalance();
					value.setEmployeeId(employeeId);
					value.setLeaveYear(year);
					value.setLeaveType(leaveType);
					value.setEntitlementDays(entitlement);
					return balances.saveAndFlush(value);
				});
	}

	private void append(
			String employeeId,
			int year,
			String leaveType,
			BigDecimal days,
			String reason,
			String sourceType,
			String sourceId,
			String operator) {
		LeaveBalanceAdjustment value = new LeaveBalanceAdjustment();
		value.setEmployeeId(employeeId);
		value.setLeaveYear(year);
		value.setLeaveType(leaveType);
		value.setChangeDays(days);
		value.setReason(reason);
		value.setSourceType(sourceType);
		value.setSourceId(sourceId);
		value.setOperatorUsername(operator);
		adjustments.save(value);
	}
}
