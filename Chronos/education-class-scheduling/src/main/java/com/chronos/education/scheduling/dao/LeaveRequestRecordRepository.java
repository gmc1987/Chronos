package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.LeaveRequestRecord;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface LeaveRequestRecordRepository extends JpaRepository<LeaveRequestRecord, String> {
	boolean existsByWorkflowInstanceId(String workflowInstanceId);
	List<LeaveRequestRecord> findByApplicantTypeAndApplicantIdOrderByStartDateDesc(
			String applicantType,
			String applicantId);
	List<LeaveRequestRecord> findByCancellationStatusOrderByCancellationRequestedAtAsc(String status);
	List<LeaveRequestRecord> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(
			LocalDate endDate,
			LocalDate startDate);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<LeaveRequestRecord> findLockedById(String id);
	boolean existsByApplicantTypeAndApplicantIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
			String applicantType,
			String applicantId,
			String status,
			LocalDate endDate,
			LocalDate startDate);
}
