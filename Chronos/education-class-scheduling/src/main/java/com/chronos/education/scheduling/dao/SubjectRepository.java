package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.Subject;

public interface SubjectRepository extends JpaRepository<Subject, String> {
	List<Subject> findAllByOrderBySortOrderAscSubjectCodeAsc();
	Page<Subject> findAllByOrderBySortOrderAscSubjectCodeAsc(Pageable pageable);
}
