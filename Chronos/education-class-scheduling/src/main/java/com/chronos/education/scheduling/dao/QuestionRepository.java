package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.Question;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, String> {
	List<Question> findByBankIdAndArchivedFalseOrderByIdDesc(String bankId);
}
