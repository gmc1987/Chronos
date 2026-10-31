package com.chronos.education.internship.dao;

import com.chronos.education.internship.model.InternshipPosition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InternshipPositionRepository extends JpaRepository<InternshipPosition, String> {
	List<InternshipPosition> findBySchoolIdOrderByCreateTimeDesc(String schoolId);
	long countByCompanyIdAndStatus(String companyId, String status);
}
