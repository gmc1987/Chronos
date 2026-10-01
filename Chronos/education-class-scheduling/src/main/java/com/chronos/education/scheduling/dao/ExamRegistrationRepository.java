package com.chronos.education.scheduling.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.scheduling.model.ExamRegistration;

public interface ExamRegistrationRepository extends JpaRepository<ExamRegistration, String> {
	Optional<ExamRegistration> findBySessionIdAndStudentId(String sessionId, String studentId);
	List<ExamRegistration> findBySessionIdOrderBySubmittedAtAsc(String sessionId);
	long countBySessionIdAndStatusIn(String sessionId, List<String> statuses);
}
