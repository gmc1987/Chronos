package com.chronos.education.scheduling.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.ExamItemScore;

public interface ExamItemScoreRepository extends JpaRepository<ExamItemScore, String> {
	List<ExamItemScore> findByItemId(String itemId);
	Optional<ExamItemScore> findByItemIdAndCandidateId(String itemId, String candidateId);
	boolean existsByItemId(String itemId);
	boolean existsByCandidateId(String candidateId);
}
