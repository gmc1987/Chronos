package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.TeacherEmploymentChange;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface TeacherEmploymentChangeRepository
		extends JpaRepository<TeacherEmploymentChange, String> {
	Page<TeacherEmploymentChange> findByTeacherIdOrderByEffectiveDateDesc(
			String teacherId,
			Pageable pageable);
	boolean existsByTeacherIdAndStatus(String teacherId, String status);
	List<TeacherEmploymentChange> findTop100ByStatusAndEffectiveDateLessThanEqualOrderByEffectiveDateAsc(
			String status,
			LocalDate effectiveDate);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<TeacherEmploymentChange> findLockedById(String id);
}
