package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.ExamCandidate;

public interface ExamCandidateRepository extends JpaRepository<ExamCandidate, String> {
	List<ExamCandidate> findByRoomIdOrderBySeatNoAsc(String roomId);
	boolean existsByRoomIdAndStudentId(String roomId, String studentId);
}
