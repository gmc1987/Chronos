package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.dao.LeaveBalanceAdjustmentRepository;
import com.chronos.education.scheduling.dao.LeaveRequestRecordRepository;
import com.chronos.education.scheduling.dao.StaffLeaveBalanceRepository;
import com.chronos.education.scheduling.model.LeaveBalanceAdjustment;
import com.chronos.education.scheduling.model.LeaveRequestRecord;
import com.chronos.education.scheduling.model.StaffLeaveBalance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StaffLeaveBalanceServiceTest {
	private StaffLeaveBalanceRepository balances;
	private LeaveBalanceAdjustmentRepository adjustments;
	private LeaveRequestRecordRepository leaveRequests;
	private StaffLeaveBalanceService service;
	private StaffLeaveBalance balance;

	@BeforeEach
	void setUp() {
		balances = mock(StaffLeaveBalanceRepository.class);
		adjustments = mock(LeaveBalanceAdjustmentRepository.class);
		leaveRequests = mock(LeaveRequestRecordRepository.class);
		service = new StaffLeaveBalanceService(balances, adjustments, leaveRequests);
		balance = new StaffLeaveBalance();
		balance.setId("balance-1");
		balance.setEmployeeId("employee-1");
		balance.setLeaveYear(2026);
		balance.setLeaveType("ANNUAL");
		balance.setEntitlementDays(new BigDecimal("10"));
		balance.setCarryoverDays(BigDecimal.ZERO);
		balance.setAdjustmentDays(BigDecimal.ZERO);
		balance.setConsumedDays(new BigDecimal("8"));
		when(balances.findByEmployeeIdAndLeaveYearAndLeaveType("employee-1", 2026, "ANNUAL"))
				.thenReturn(Optional.of(balance));
		when(balances.findLocked("employee-1", 2026, "ANNUAL")).thenReturn(Optional.of(balance));
		when(balances.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void pendingRequestsReserveAvailableBalance() {
		LeaveRequestRecord pending = new LeaveRequestRecord();
		pending.setLeaveType("ANNUAL");
		pending.setRequestedDays(BigDecimal.ONE);
		when(leaveRequests
				.findByApplicantTypeAndApplicantIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
						"STAFF",
						"employee-1",
						List.of("PENDING"),
						LocalDate.of(2026, 12, 31),
						LocalDate.of(2026, 1, 1)))
				.thenReturn(List.of(pending));

		assertThatThrownBy(() -> service.assertAvailable(
				"employee-1", "ANNUAL", LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("可用 1 天，申请 2 天");
	}

	@Test
	void approvalDeductsBalanceAndWritesLedger() {
		LeaveRequestRecord record = approvedRecord();

		service.deduct(record, "approver");

		assertThat(balance.getConsumedDays()).isEqualByComparingTo("10");
		assertThat(record.getBalanceDeducted()).isTrue();
		verify(adjustments).save(any(LeaveBalanceAdjustment.class));
	}

	@Test
	void earlyReturnRefundsOnlyUnusedDays() {
		balance.setConsumedDays(new BigDecimal("10"));
		LeaveRequestRecord record = approvedRecord();
		record.setBalanceDeducted(true);

		service.refund(record, BigDecimal.ONE, "approver", "提前返岗");

		assertThat(balance.getConsumedDays()).isEqualByComparingTo("9");
		verify(adjustments).save(any(LeaveBalanceAdjustment.class));
	}

	@Test
	void rejectsCrossYearRequest() {
		assertThatThrownBy(() -> service.assertAvailable(
				"employee-1", "ANNUAL", LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("跨年度请假请按自然年度分开申请");
	}

	private LeaveRequestRecord approvedRecord() {
		LeaveRequestRecord record = new LeaveRequestRecord();
		record.setId("leave-1");
		record.setApplicantType("STAFF");
		record.setApplicantId("employee-1");
		record.setLeaveType("ANNUAL");
		record.setStartDate(LocalDate.of(2026, 10, 10));
		record.setEndDate(LocalDate.of(2026, 10, 11));
		record.setRequestedDays(new BigDecimal("2"));
		record.setBalanceDeducted(false);
		return record;
	}
}
