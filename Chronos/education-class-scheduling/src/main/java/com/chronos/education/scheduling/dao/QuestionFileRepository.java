package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionFileRepository extends JpaRepository<QuestionFile, QuestionFileId> {
	void deleteByQuestionId(String questionId);
	List<QuestionFile> findByQuestionId(String questionId);
}
