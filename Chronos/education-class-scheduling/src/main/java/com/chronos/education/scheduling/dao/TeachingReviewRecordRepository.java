package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.TeachingReviewRecord;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeachingReviewRecordRepository extends JpaRepository<TeachingReviewRecord, String> {
	Optional<TeachingReviewRecord> findTopByResourceTypeAndResourceIdOrderBySubmissionNoDesc(String resourceType, String resourceId);
	java.util.List<TeachingReviewRecord> findByResourceTypeAndResourceIdOrderBySubmissionNoDesc(String resourceType, String resourceId);
	default Optional<TeachingReviewRecord> findByResourceTypeAndResourceId(String resourceType, String resourceId) {
		return findTopByResourceTypeAndResourceIdOrderBySubmissionNoDesc(resourceType, resourceId);
	}
	Optional<TeachingReviewRecord> findByBusinessKey(String businessKey);
	Optional<TeachingReviewRecord> findByResourceTypeAndResourceIdAndIdempotencyKey(
			String resourceType, String resourceId, String idempotencyKey);
	Optional<TeachingReviewRecord> findTopByResourceTypeAndResourceIdAndVersionIdOrderBySubmissionNoDesc(
			String resourceType, String resourceId, String versionId);
	List<TeachingReviewRecord> findByStatusInOrderBySubmittedAtAsc(java.util.Collection<String> statuses);
}
