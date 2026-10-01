package com.chronos.education.scheduling.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.scheduling.model.ExamAdmissionTicket;

public interface ExamAdmissionTicketRepository extends JpaRepository<ExamAdmissionTicket, String> {
	Optional<ExamAdmissionTicket> findByCandidateIdAndPublishedVersion(String candidateId, Integer publishedVersion);
	List<ExamAdmissionTicket> findByCandidateIdOrderByPublishedVersionDesc(String candidateId);
}
