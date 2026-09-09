package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.SchedulePlanVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchedulePlanVersionRepository extends JpaRepository<SchedulePlanVersion, String> {
	List<SchedulePlanVersion> findBySemesterCodeOrderByVersionNoDesc(String semesterCode);

	Optional<SchedulePlanVersion> findFirstBySemesterCodeOrderByVersionNoDesc(String semesterCode);
}
