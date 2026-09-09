package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.LeaveRequestRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveRequestRecordRepository extends JpaRepository<LeaveRequestRecord, String> {
	boolean existsByWorkflowInstanceId(String workflowInstanceId);
}
