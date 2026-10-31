package com.chronos.education.internship.dao;

import com.chronos.education.internship.model.InternshipCompany;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InternshipCompanyRepository extends JpaRepository<InternshipCompany, String> {
	List<InternshipCompany> findBySchoolIdOrderByCreateTimeDesc(String schoolId);
	boolean existsBySchoolIdAndCompanyCode(String schoolId, String companyCode);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select value from InternshipCompany value where value.id = :id")
	Optional<InternshipCompany> findForUpdate(@Param("id") String id);
}
