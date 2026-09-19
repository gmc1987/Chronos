package com.chronos.education.supervision.dao;
import com.chronos.education.supervision.model.SupervisionPlan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SupervisionPlanRepository extends JpaRepository<SupervisionPlan, String> {
	List<SupervisionPlan> findBySchoolIdOrderByCreateTimeDesc(String schoolId);
}
