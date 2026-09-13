package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.LessonPlan;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LessonPlanRepository extends JpaRepository<LessonPlan, String> {
	java.util.List<LessonPlan> findByOfferingIdAndArchivedFalseOrderByCreateTimeDesc(String offeringId);
}
