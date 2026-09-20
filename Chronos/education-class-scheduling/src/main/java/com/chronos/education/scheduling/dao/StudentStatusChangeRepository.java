package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.StudentStatusChange;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface StudentStatusChangeRepository extends JpaRepository<StudentStatusChange, String> {
	Page<StudentStatusChange> findByStudentIdOrderByRequestedAtDesc(
			String studentId,
			Pageable pageable);

	boolean existsByStudentIdAndStatus(String studentId, String status);

	long countByStudentIdInAndStatus(List<String> studentIds, String status);

	List<StudentStatusChange> findTop20ByStudentIdInAndStatusOrderByRequestedAtDesc(
			List<String> studentIds,
			String status);

	List<StudentStatusChange> findTop100ByStatusAndEffectiveDateLessThanEqualOrderByEffectiveDateAsc(
			String status,
			LocalDate effectiveDate);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<StudentStatusChange> findLockedById(String id);
}
