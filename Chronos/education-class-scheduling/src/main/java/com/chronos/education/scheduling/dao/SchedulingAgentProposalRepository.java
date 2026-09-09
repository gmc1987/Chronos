package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.SchedulingAgentProposal;

public interface SchedulingAgentProposalRepository extends JpaRepository<SchedulingAgentProposal, String> {
	List<SchedulingAgentProposal> findBySemesterCodeOrderByCreateTimeDesc(String semesterCode);
}
