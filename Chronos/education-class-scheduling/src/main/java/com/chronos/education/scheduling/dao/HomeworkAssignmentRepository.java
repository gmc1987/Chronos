package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.HomeworkAssignment;

public interface HomeworkAssignmentRepository extends JpaRepository<HomeworkAssignment, String> {
	List<HomeworkAssignment> findByOfferingIdOrderByDueAtDescCreateTimeDesc(String offeringId);
	List<HomeworkAssignment> findByOfferingIdInAndStatusOrderByDueAtDescCreateTimeDesc(
			List<String> offeringIds, String status);
	List<HomeworkAssignment> findByOfferingIdInAndStatusInOrderByDueAtDescCreateTimeDesc(
			List<String> offeringIds, List<String> statuses);
}
