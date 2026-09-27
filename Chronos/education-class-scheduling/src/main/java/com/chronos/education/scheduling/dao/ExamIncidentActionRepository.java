package com.chronos.education.scheduling.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.scheduling.model.ExamIncidentAction;

public interface ExamIncidentActionRepository extends JpaRepository<ExamIncidentAction, String> {
	List<ExamIncidentAction> findByIncidentIdOrderByActionAtAsc(String incidentId);
}
