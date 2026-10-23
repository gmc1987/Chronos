package com.chronos.education.scheduling.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.Subject;

public interface SubjectRepository extends JpaRepository<Subject, String> {
	Optional<Subject> findBySubjectCode(String subjectCode);
	List<Subject> findAllByOrderBySortOrderAscSubjectCodeAsc();
	Page<Subject> findAllByOrderBySortOrderAscSubjectCodeAsc(Pageable pageable);
}
