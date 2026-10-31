package com.chronos.education.academic.dao;

import com.chronos.education.academic.model.AcademicProgramCourse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicProgramCourseRepository extends JpaRepository<AcademicProgramCourse, String> {
	List<AcademicProgramCourse> findByProgramIdOrderBySuggestedTermNoAscSortOrderAsc(String programId);
	Optional<AcademicProgramCourse> findByProgramIdAndCourseId(String programId, String courseId);
	long countByProgramId(String programId);
}
