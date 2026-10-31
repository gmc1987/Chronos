package com.chronos.facility.dao;

import com.chronos.facility.model.PreventiveMaintenancePlan;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PreventiveMaintenancePlanRepository extends JpaRepository<PreventiveMaintenancePlan, String> {
	List<PreventiveMaintenancePlan> findAllByOrderByNextDueDate();
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select plan from PreventiveMaintenancePlan plan where plan.id = :id")
	Optional<PreventiveMaintenancePlan> findForUpdate(@Param("id") String id);
}
