package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.LeaveBalanceAdjustment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveBalanceAdjustmentRepository extends JpaRepository<LeaveBalanceAdjustment, String> {
	List<LeaveBalanceAdjustment> findByEmployeeIdAndLeaveYearOrderByCreateTimeDesc(
			String employeeId, Integer leaveYear);
}
