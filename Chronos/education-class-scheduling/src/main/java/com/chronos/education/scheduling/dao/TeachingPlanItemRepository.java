package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.TeachingPlanItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TeachingPlanItemRepository extends JpaRepository<TeachingPlanItem, String> {
	List<TeachingPlanItem> findByPlanIdOrderBySortOrderAsc(String planId);
	void deleteByPlanId(String planId);
}
