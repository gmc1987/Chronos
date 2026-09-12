package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.KnowledgePoint;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, String> {
	List<KnowledgePoint> findBySubjectIdAndEnabledTrueOrderBySortOrderAsc(String subjectId);
}
