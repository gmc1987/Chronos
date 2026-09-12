package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.ScheduleGenerationJob;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleGenerationJobRepository extends JpaRepository<ScheduleGenerationJob, String> {
	List<ScheduleGenerationJob> findTop20BySemesterCodeOrderByCreateTimeDesc(String semesterCode);
	List<ScheduleGenerationJob> findByStatusIn(List<String> statuses);
}
