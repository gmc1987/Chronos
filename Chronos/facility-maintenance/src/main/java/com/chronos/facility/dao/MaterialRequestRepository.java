package com.chronos.facility.dao;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.chronos.facility.model.MaterialRequest;

public interface MaterialRequestRepository
		extends JpaRepository<MaterialRequest, String>, JpaSpecificationExecutor<MaterialRequest> {
	List<MaterialRequest> findByRepairOrderIdOrderByCreateTime(String repairOrderId);
	Page<MaterialRequest> findByApplicantUsernameOrderByCreateTimeDesc(String username, Pageable pageable);
	boolean existsByRepairOrderIdAndStatusIn(String repairOrderId, java.util.Collection<String> statuses);
	@Query(value = "select nextval('fm_material_request_no_seq')", nativeQuery = true)
	long nextRequestSequence();
}
