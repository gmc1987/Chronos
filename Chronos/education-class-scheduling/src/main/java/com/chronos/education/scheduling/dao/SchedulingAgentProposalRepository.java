package com.chronos.education.scheduling.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chronos.education.scheduling.model.SchedulingAgentProposal;
import jakarta.persistence.LockModeType;

public interface SchedulingAgentProposalRepository extends JpaRepository<SchedulingAgentProposal, String> {
	List<SchedulingAgentProposal> findBySemesterCodeOrderByCreateTimeDesc(String semesterCode);
	Page<SchedulingAgentProposal> findBySemesterCodeOrderByCreateTimeDesc(String semesterCode, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select proposal from SchedulingAgentProposal proposal where proposal.id = :id")
	Optional<SchedulingAgentProposal> findLockedById(@Param("id") String id);
}
