package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.QuestionVersion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionVersionRepository extends JpaRepository<QuestionVersion, String> {
	Optional<QuestionVersion> findByQuestionIdAndVersionNo(String questionId, Integer versionNo);
}
