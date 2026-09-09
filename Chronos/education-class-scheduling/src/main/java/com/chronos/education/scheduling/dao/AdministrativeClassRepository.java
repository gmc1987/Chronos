package com.chronos.education.scheduling.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.scheduling.model.AdministrativeClass;

public interface AdministrativeClassRepository extends JpaRepository<AdministrativeClass, String> {
	List<AdministrativeClass> findAllByOrderByGradeYearDescClassCodeAsc();
	long countByMajorId(String majorId);

	long countByGradeId(String gradeId);
}
