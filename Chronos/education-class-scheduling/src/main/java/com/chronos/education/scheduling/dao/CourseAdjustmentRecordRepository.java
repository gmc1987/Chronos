package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.CourseAdjustmentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseAdjustmentRecordRepository extends JpaRepository<CourseAdjustmentRecord, String> {
	boolean existsByWorkflowInstanceId(String workflowInstanceId);
}
