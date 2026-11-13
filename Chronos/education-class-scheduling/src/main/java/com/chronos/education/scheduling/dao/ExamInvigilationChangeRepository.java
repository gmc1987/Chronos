package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.ExamInvigilationChange;

public interface ExamInvigilationChangeRepository extends JpaRepository<ExamInvigilationChange, String> {
	@org.springframework.data.jpa.repository.Query("""
			select c from ExamInvigilationChange c
			join ExamInvigilation a on a.id = c.assignmentId
			join ExamRoom r on r.id = a.roomId
			join ExamSession s on s.id = r.sessionId
			join ExamPlan p on p.id = s.planId
			where c.status = 'PENDING' and p.organizationId in :organizationIds
			order by c.createTime desc
			""")
	List<ExamInvigilationChange> findPendingByOrganizationIds(
			@org.springframework.data.repository.query.Param("organizationIds") java.util.Set<String> organizationIds);

	List<ExamInvigilationChange> findByStatusOrderByCreateTimeDesc(String status);
	List<ExamInvigilationChange> findByRequestedByOrderByCreateTimeDesc(String requestedBy);
	boolean existsByAssignmentIdAndStatus(String assignmentId, String status);
}
