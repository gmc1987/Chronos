package com.chronos.facility.dao;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.chronos.facility.model.RepairOrder;

public interface RepairOrderRepository
		extends JpaRepository<RepairOrder, String>, JpaSpecificationExecutor<RepairOrder> {
	Optional<RepairOrder> findByOrderNo(String orderNo);
	Page<RepairOrder> findByReporterUsernameOrderByCreateTimeDesc(String username, Pageable pageable);
	Page<RepairOrder> findByTechnicianUsernameOrderByCreateTimeDesc(String username, Pageable pageable);
	long countByTechnicianIdAndStatusIn(String technicianId, java.util.Collection<String> statuses);
	@Query(value = "select nextval('fm_repair_order_no_seq')", nativeQuery = true)
	long nextOrderSequence();
}
