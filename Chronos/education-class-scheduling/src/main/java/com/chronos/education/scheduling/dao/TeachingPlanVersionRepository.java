package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.TeachingPlanVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TeachingPlanVersionRepository extends JpaRepository<TeachingPlanVersion, String> {
	List<TeachingPlanVersion> findByPlanIdOrderByVersionNoDesc(String planId);
	Optional<TeachingPlanVersion> findByPlanIdAndVersionNo(String planId, Integer versionNo);
}
