package com.chronos.education.scheduling.dao;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.scheduling.model.CourseCatalog;

public interface CourseCatalogRepository extends JpaRepository<CourseCatalog, String> {
	List<CourseCatalog> findAllByOrderByCourseCode();
	Page<CourseCatalog> findAllByOrderByCourseCode(Pageable pageable);

	long countBySubjectId(String subjectId);
}
