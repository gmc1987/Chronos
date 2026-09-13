package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionFileRepository extends JpaRepository<QuestionFile, QuestionFileId> {
	void deleteByQuestionId(String questionId);
}
