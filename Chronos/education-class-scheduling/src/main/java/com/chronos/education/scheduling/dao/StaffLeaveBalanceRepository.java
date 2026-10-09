package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.StaffLeaveBalance;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffLeaveBalanceRepository extends JpaRepository<StaffLeaveBalance, String> {
	List<StaffLeaveBalance> findByEmployeeIdAndLeaveYearOrderByLeaveType(String employeeId, Integer leaveYear);

	Optional<StaffLeaveBalance> findByEmployeeIdAndLeaveYearAndLeaveType(
			String employeeId, Integer leaveYear, String leaveType);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select balance from StaffLeaveBalance balance
			where balance.employeeId = :employeeId
			  and balance.leaveYear = :leaveYear
			  and balance.leaveType = :leaveType
			""")
	Optional<StaffLeaveBalance> findLocked(
			@Param("employeeId") String employeeId,
			@Param("leaveYear") Integer leaveYear,
			@Param("leaveType") String leaveType);
}
