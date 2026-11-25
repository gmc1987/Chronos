package com.chronos.education.internship.dao;

import com.chronos.education.internship.model.InternshipPosition;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface InternshipPositionRepository extends JpaRepository<InternshipPosition, String> {
	List<InternshipPosition> findBySchoolIdOrderByCreateTimeDesc(String schoolId);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select position from InternshipPosition position where position.id = :id")
	Optional<InternshipPosition> findForUpdate(String id);

	long countByCompanyIdAndStatus(String companyId, String status);
}
