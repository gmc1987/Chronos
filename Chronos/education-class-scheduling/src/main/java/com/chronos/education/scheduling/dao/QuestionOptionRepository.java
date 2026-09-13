package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.QuestionOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, String> {
	List<QuestionOption> findByQuestionIdOrderBySortOrderAscOptionKeyAsc(String questionId);
	void deleteByQuestionId(String questionId);
}
