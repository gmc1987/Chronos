package com.chronos.education.scheduling.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.scheduling.model.ExamIncident;

public interface ExamIncidentRepository extends JpaRepository<ExamIncident, String> {
	List<ExamIncident> findBySessionIdOrderByReportedAtDesc(String sessionId);
}
