package com.chronos.education.scheduling.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.scheduling.model.AcademicTerm;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, String> {
	List<AcademicTerm> findAllByOrderByStartDateDesc();
	java.util.Optional<AcademicTerm> findFirstByCurrentTermTrueAndStatusOrderByStartDateDesc(String status);
}
