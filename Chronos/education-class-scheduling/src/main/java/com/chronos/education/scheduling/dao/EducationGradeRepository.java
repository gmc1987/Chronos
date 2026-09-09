package com.chronos.education.scheduling.dao;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.EducationGrade;

public interface EducationGradeRepository extends JpaRepository<EducationGrade, String> {
	List<EducationGrade> findAllByOrderByEnrollmentYearDescSortOrderAsc();
	Page<EducationGrade> findAllByOrderByEnrollmentYearDescSortOrderAsc(Pageable pageable);
}
