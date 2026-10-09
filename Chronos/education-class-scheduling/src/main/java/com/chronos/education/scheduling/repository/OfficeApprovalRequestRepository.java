package com.chronos.education.scheduling.repository;

import com.chronos.education.scheduling.model.OfficeApprovalRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfficeApprovalRequestRepository extends JpaRepository<OfficeApprovalRequest, String> {
	List<OfficeApprovalRequest> findByEmployeeIdOrderByCreateTimeDesc(String employeeId);
	List<OfficeApprovalRequest> findAllByOrderByCreateTimeDesc();
	Optional<OfficeApprovalRequest> findByWorkflowInstanceId(String workflowInstanceId);

	@Query("""
			select count(r) from OfficeApprovalRequest r
			where r.resourceId = :resourceId
			  and r.status in ('PENDING', 'APPROVED', 'IN_USE')
			  and r.startAt < :endAt and r.endAt > :startAt
			""")
	long countConflicts(
			@Param("resourceId") String resourceId,
			@Param("startAt") LocalDateTime startAt,
			@Param("endAt") LocalDateTime endAt);
}
