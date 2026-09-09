package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.CourseAdjustmentRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseAdjustmentRecordRepository extends JpaRepository<CourseAdjustmentRecord, String> {
	boolean existsByWorkflowInstanceId(String workflowInstanceId);

	Optional<CourseAdjustmentRecord> findByWorkflowInstanceId(String workflowInstanceId);

	List<CourseAdjustmentRecord> findByStatusOrderByCreateTimeDesc(String status);
}
