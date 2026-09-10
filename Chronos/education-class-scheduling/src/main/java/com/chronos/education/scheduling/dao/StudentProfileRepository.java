package com.chronos.education.scheduling.dao;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.chronos.education.scheduling.model.StudentProfile;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, String> {
	List<StudentProfile> findAllByOrderByStudentNo();
	Page<StudentProfile> findAllByOrderByStudentNo(Pageable pageable);

	@Query("""
			select value from StudentProfile value
			where value.administrativeClassId in :classIds or value.gradeId in :gradeIds
			order by value.studentNo
			""")
	List<StudentProfile> findVisible(
			@Param("classIds") List<String> classIds,
			@Param("gradeIds") List<String> gradeIds);

	@Query("""
			select value from StudentProfile value
			where value.administrativeClassId in :classIds or value.gradeId in :gradeIds
			order by value.studentNo
			""")
	Page<StudentProfile> findVisible(
			@Param("classIds") List<String> classIds,
			@Param("gradeIds") List<String> gradeIds,
			Pageable pageable);
	List<StudentProfile> findByAdministrativeClassId(String administrativeClassId);
	long countByMajorId(String majorId);

	long countByGradeId(String gradeId);
	long countByAdministrativeClassId(String administrativeClassId);
}
