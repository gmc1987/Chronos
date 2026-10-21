package com.chronos.education.scheduling.dao;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.chronos.education.scheduling.model.AdmissionCandidate;

public interface AdmissionCandidateRepository extends JpaRepository<AdmissionCandidate, String> {
	Optional<AdmissionCandidate> findByAdmissionYearAndCandidateNo(Integer admissionYear, String candidateNo);

	@Query("""
		select value from AdmissionCandidate value
		where (:status = '' or value.admissionStatus = :status)
		  and (:keyword = '' or lower(value.studentName) like concat('%', :keyword, '%')
		       or lower(value.candidateNo) like concat('%', :keyword, '%')
		       or lower(coalesce(value.admissionNo, '')) like concat('%', :keyword, '%'))
		order by value.admissionYear desc, value.createTime desc
		""")
	Page<AdmissionCandidate> search(@Param("status") String status, @Param("keyword") String keyword, Pageable pageable);
}
