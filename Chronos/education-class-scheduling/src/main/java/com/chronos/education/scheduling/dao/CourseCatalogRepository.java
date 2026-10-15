package com.chronos.education.scheduling.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.scheduling.model.CourseCatalog;

public interface CourseCatalogRepository extends JpaRepository<CourseCatalog, String> {
	Optional<CourseCatalog> findByCourseCode(String courseCode);
	List<CourseCatalog> findAllByOrderByCourseCode();
	Page<CourseCatalog> findAllByOrderByCourseCode(Pageable pageable);

	long countBySubjectId(String subjectId);
}
