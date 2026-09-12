package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.TeachingReviewRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeachingReviewRecordRepository extends JpaRepository<TeachingReviewRecord, String> {
	Optional<TeachingReviewRecord> findByResourceTypeAndResourceId(String resourceType, String resourceId);
	Optional<TeachingReviewRecord> findByBusinessKey(String businessKey);
}
