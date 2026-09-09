package com.chronos.education.scheduling.dao;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.chronos.education.scheduling.model.AcademicTerm;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, String> {
	List<AcademicTerm> findAllByOrderByStartDateDesc();
	Page<AcademicTerm> findAllByOrderByStartDateDesc(Pageable pageable);
	Optional<AcademicTerm> findFirstByCurrentTermTrueAndStatusOrderByStartDateDesc(String status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<AcademicTerm> findByTermCode(String termCode);
}
