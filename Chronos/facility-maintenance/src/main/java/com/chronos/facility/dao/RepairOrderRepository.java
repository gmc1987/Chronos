package com.chronos.facility.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.chronos.facility.model.RepairOrder;

import jakarta.persistence.LockModeType;

public interface RepairOrderRepository
		extends JpaRepository<RepairOrder, String>, JpaSpecificationExecutor<RepairOrder> {
	Optional<RepairOrder> findByOrderNo(String orderNo);
	Page<RepairOrder> findByReporterUsernameOrderByCreateTimeDesc(String username, Pageable pageable);
	Page<RepairOrder> findByTechnicianUsernameOrderByCreateTimeDesc(String username, Pageable pageable);
	long countByTechnicianIdAndStatusIn(String technicianId, java.util.Collection<String> statuses);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select repair from RepairOrder repair
			where repair.status = 'DISPATCHED'
			  and repair.dispatchDeadline is not null
			  and repair.dispatchDeadline <= :now
			order by case repair.urgency when 'CRITICAL' then 0 when 'URGENT' then 1 else 2 end,
			         repair.dispatchDeadline asc
			""")
	List<RepairOrder> lockExpiredDispatches(LocalDateTime now, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select repair from RepairOrder repair
			where repair.status = 'PENDING_DISPATCH'
			  and repair.dispatchMode = 'AUTO'
			  and repair.nextDispatchRetryAt is not null
			  and repair.nextDispatchRetryAt <= :now
			order by case repair.urgency when 'CRITICAL' then 0 when 'URGENT' then 1 else 2 end,
			         repair.nextDispatchRetryAt asc
			""")
	List<RepairOrder> lockPendingAutoDispatches(LocalDateTime now, Pageable pageable);
	@Query(value = "select nextval('fm_repair_order_no_seq')", nativeQuery = true)
	long nextOrderSequence();
}
