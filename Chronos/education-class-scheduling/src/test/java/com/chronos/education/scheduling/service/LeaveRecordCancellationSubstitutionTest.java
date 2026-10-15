package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.LeaveRequestRecordRepository;
import com.chronos.education.scheduling.dao.ScheduleDateExceptionRepository;
import com.chronos.education.scheduling.model.LeaveRequestRecord;
import com.chronos.education.scheduling.model.ScheduleDateException;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.workflow.WorkflowService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LeaveRecordCancellationSubstitutionTest {
	private LeaveRequestRecordRepository records;
	private ScheduleDateExceptionRepository dateExceptions;
	private StaffLeaveBalanceService balances;
	private LeaveRecordService service;

	@BeforeEach
	void setUp() {
		records = mock(LeaveRequestRecordRepository.class);
		dateExceptions = mock(ScheduleDateExceptionRepository.class);
		balances = mock(StaffLeaveBalanceService.class);
		service = new LeaveRecordService(records, mock(EducationUserBindingRepository.class),
				mock(EducationApplicantResolver.class), mock(WorkflowService.class),
				mock(IAuditLogService.class), mock(IAdminUserRepository.class),
				mock(IEmployeeRepository.class), balances, dateExceptions);
	}

	@Test
	void approvedEarlyReturnCancelsOnlyLinkedSubstitutionsAfterActualEnd() {
		LeaveRequestRecord leave = pendingCancellation();
		ScheduleDateException future = new ScheduleDateException();
		future.setStatus("ACTIVE");
		future.setSourceDate(LocalDate.of(2026, 10, 22));
		when(records.findLockedById("leave-1")).thenReturn(Optional.of(leave));
		when(balances.requestedDays(leave.getStartDate(), leave.getActualEndDate()))
				.thenReturn(new BigDecimal("2"));
		when(dateExceptions.findByLeaveRecordIdAndExceptionTypeAndStatusAndSourceDateAfter(
				"leave-1", "SUBSTITUTE", "ACTIVE", leave.getActualEndDate()))
				.thenReturn(List.of(future));
		when(records.save(leave)).thenReturn(leave);

		service.decideCancellation("reviewer", "leave-1", true, "返岗");

		assertThat(leave.getStatus()).isEqualTo("PARTIALLY_CANCELLED");
		assertThat(future.getStatus()).isEqualTo("CANCELLED");
		verify(dateExceptions).saveAll(List.of(future));
		verify(balances).refund(leave, new BigDecimal("3"), "reviewer", "销假审批通过返还");
	}

	@Test
	void rejectedEarlyReturnDoesNotCancelSubstitutions() {
		LeaveRequestRecord leave = pendingCancellation();
		when(records.findLockedById("leave-1")).thenReturn(Optional.of(leave));
		when(records.save(leave)).thenReturn(leave);

		service.decideCancellation("reviewer", "leave-1", false, "不通过");

		assertThat(leave.getStatus()).isEqualTo("APPROVED");
		verify(dateExceptions, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
	}

	private LeaveRequestRecord pendingCancellation() {
		LeaveRequestRecord leave = new LeaveRequestRecord();
		ReflectionTestUtils.setField(leave, "id", "leave-1");
		leave.setStartDate(LocalDate.of(2026, 10, 19));
		leave.setEndDate(LocalDate.of(2026, 10, 23));
		leave.setActualEndDate(LocalDate.of(2026, 10, 20));
		leave.setRequestedDays(new BigDecimal("5"));
		leave.setStatus("APPROVED");
		leave.setCancellationStatus("PENDING");
		return leave;
	}
}
