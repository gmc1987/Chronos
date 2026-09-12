package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.SchedulePolicy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchedulePolicyRepository extends JpaRepository<SchedulePolicy, String> {
	Optional<SchedulePolicy> findBySemesterCode(String semesterCode);
}
