package com.chronos.facility.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.chronos.facility.model.MaintenanceTechnician;

import jakarta.persistence.LockModeType;

public interface MaintenanceTechnicianRepository extends JpaRepository<MaintenanceTechnician, String> {
	Optional<MaintenanceTechnician> findByEmployeeId(String employeeId);
	Optional<MaintenanceTechnician> findByUsername(String username);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select technician from MaintenanceTechnician technician
			where technician.active = true
			  and technician.autoDispatch = true
			  and technician.workStatus = 'AVAILABLE'
			  and technician.currentLoad < technician.maxConcurrent
			order by technician.currentLoad asc, technician.lastUpdateTime asc, technician.createTime asc
			""")
	List<MaintenanceTechnician> lockAutoDispatchCandidates();
}
