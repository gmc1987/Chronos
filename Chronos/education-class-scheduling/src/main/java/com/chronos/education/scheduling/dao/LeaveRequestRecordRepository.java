package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.LeaveRequestRecord;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveRequestRecordRepository extends JpaRepository<LeaveRequestRecord, String> {
	boolean existsByWorkflowInstanceId(String workflowInstanceId);
	boolean existsByApplicantTypeAndApplicantIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
			String applicantType,
			String applicantId,
			String status,
			LocalDate endDate,
			LocalDate startDate);
}
