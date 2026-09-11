package com.chronos.education.scheduling.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.BellSchedule;

public interface BellScheduleRepository extends JpaRepository<BellSchedule, String> {
	List<BellSchedule> findByAcademicTermIdOrderByScheduleName(String academicTermId);
	List<BellSchedule> findByAcademicTermIdAndCampusIdAndDefaultScheduleTrue(
			String academicTermId,
			String campusId);
	Optional<BellSchedule> findFirstByAcademicTermIdAndCampusIdAndDefaultScheduleTrueAndStatus(
			String academicTermId,
			String campusId,
			String status);
	boolean existsByAcademicTermIdAndStatus(String academicTermId, String status);
}
