package com.chronos.education.scheduling.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.scheduling.model.CourseCatalog;

public interface CourseCatalogRepository extends JpaRepository<CourseCatalog, String> {
	List<CourseCatalog> findAllByOrderByCourseCode();

	long countBySubjectId(String subjectId);
}
