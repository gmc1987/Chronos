package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.CourseAdjustmentRecord;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseAdjustmentRecordRepository extends
		JpaRepository<CourseAdjustmentRecord, String>,
		JpaSpecificationExecutor<CourseAdjustmentRecord> {
	boolean existsByWorkflowInstanceId(String workflowInstanceId);

	Optional<CourseAdjustmentRecord> findByWorkflowInstanceId(String workflowInstanceId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select record from CourseAdjustmentRecord record where record.id = :id")
	Optional<CourseAdjustmentRecord> findLockedById(@Param("id") String id);

	List<CourseAdjustmentRecord> findByStatusOrderByCreateTimeDesc(String status);
}
