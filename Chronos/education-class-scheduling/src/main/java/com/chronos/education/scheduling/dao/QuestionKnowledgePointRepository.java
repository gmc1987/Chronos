package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionKnowledgePointRepository extends JpaRepository<QuestionKnowledgePoint, QuestionKnowledgePointId> {
	List<QuestionKnowledgePoint> findByQuestionId(String questionId);
	void deleteByQuestionId(String questionId);
	long countByKnowledgePointId(String knowledgePointId);
}
