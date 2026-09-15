package com.chronos.education.scheduling.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.chronos.education.scheduling.model.CourseOffering;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, String> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select offering from CourseOffering offering where offering.id = :id")
	Optional<CourseOffering> findForCombinedUpdate(@Param("id") String id);

	List<CourseOffering> findBySemesterCodeOrderByOfferingCode(String semesterCode);
	List<CourseOffering> findByTeacherIdOrderByOfferingCode(String teacherId);
	Page<CourseOffering> findBySemesterCodeOrderByOfferingCode(String semesterCode, Pageable pageable);
	boolean existsByTeacherIdAndCampusIdIn(String teacherId, List<String> campusIds);
	boolean existsByTeacherId(String teacherId);
	List<CourseOffering> findByCourseCode(String courseCode);
}
