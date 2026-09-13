package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.TeachingPlan;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TeachingPlanRepository extends JpaRepository<TeachingPlan, String> {
	java.util.List<TeachingPlan> findByOfferingIdAndArchivedFalseOrderByCreateTimeDesc(String offeringId);
}
