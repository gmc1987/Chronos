package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.QuestionBank;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionBankRepository extends JpaRepository<QuestionBank, String> {
	List<QuestionBank> findByOfferingIdAndArchivedFalse(String offeringId);
}
