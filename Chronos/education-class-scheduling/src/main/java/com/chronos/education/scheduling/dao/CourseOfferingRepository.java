package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.CourseOffering;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, String> {
	List<CourseOffering> findBySemesterCodeOrderByOfferingCode(String semesterCode);
	Page<CourseOffering> findBySemesterCodeOrderByOfferingCode(String semesterCode, Pageable pageable);
}
