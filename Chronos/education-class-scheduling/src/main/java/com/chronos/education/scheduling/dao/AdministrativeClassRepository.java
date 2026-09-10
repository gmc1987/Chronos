package com.chronos.education.scheduling.dao;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.chronos.education.scheduling.model.AdministrativeClass;

public interface AdministrativeClassRepository extends JpaRepository<AdministrativeClass, String> {
	List<AdministrativeClass> findAllByOrderByGradeYearDescClassCodeAsc();
	Page<AdministrativeClass> findAllByOrderByGradeYearDescClassCodeAsc(Pageable pageable);
	List<AdministrativeClass> findByHeadTeacherId(String headTeacherId);
	List<AdministrativeClass> findByCampusIdIn(List<String> campusIds);

	@Query("""
			select value from AdministrativeClass value
			where value.id in :classIds
			   or value.gradeId in :gradeIds
			   or value.campusId in :campusIds
			order by value.gradeYear desc, value.classCode asc
			""")
	List<AdministrativeClass> findVisible(
			@Param("classIds") List<String> classIds,
			@Param("gradeIds") List<String> gradeIds,
			@Param("campusIds") List<String> campusIds);

	@Query("""
			select value from AdministrativeClass value
			where value.id in :classIds
			   or value.gradeId in :gradeIds
			   or value.campusId in :campusIds
			order by value.gradeYear desc, value.classCode asc
			""")
	Page<AdministrativeClass> findVisible(
			@Param("classIds") List<String> classIds,
			@Param("gradeIds") List<String> gradeIds,
			@Param("campusIds") List<String> campusIds,
			Pageable pageable);
	long countByMajorId(String majorId);

	long countByGradeId(String gradeId);
}
