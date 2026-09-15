package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.ResearchMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ResearchMaterialRepository extends JpaRepository<ResearchMaterial,String> {
	java.util.List<ResearchMaterial> findByActivityId(String activityId);
}
