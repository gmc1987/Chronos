package com.chronos.education.academic.dao;

import com.chronos.education.academic.model.AcademicProgramCourse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AcademicProgramCourseRepository extends JpaRepository<AcademicProgramCourse, String> {
	List<AcademicProgramCourse> findByProgramIdOrderBySuggestedTermNoAscSortOrderAsc(String programId);
	Optional<AcademicProgramCourse> findByProgramIdAndCourseId(String programId, String courseId);
	long countByProgramId(String programId);
	@Modifying
	@Query("delete from AcademicProgramCourse item where item.programId = :programId")
	void deleteDraftCourses(@Param("programId") String programId);
}
