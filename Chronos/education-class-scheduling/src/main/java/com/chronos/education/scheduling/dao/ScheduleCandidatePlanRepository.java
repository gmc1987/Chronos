package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.ScheduleCandidatePlan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleCandidatePlanRepository
		extends JpaRepository<ScheduleCandidatePlan, String> {
	List<ScheduleCandidatePlan> findBySemesterCodeOrderByGeneratedAtDesc(
			String semesterCode);
}
