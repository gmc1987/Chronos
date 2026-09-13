package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.QuestionReference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionReferenceRepository extends JpaRepository<QuestionReference, String> {
	boolean existsByQuestionId(String questionId);
}
