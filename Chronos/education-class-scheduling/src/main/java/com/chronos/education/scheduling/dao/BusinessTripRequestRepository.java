package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.BusinessTripRequest;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface BusinessTripRequestRepository extends JpaRepository<BusinessTripRequest, String> {
	Optional<BusinessTripRequest> findByWorkflowInstanceId(String workflowInstanceId);
	List<BusinessTripRequest> findByEmployeeIdOrderByStartDateDesc(String employeeId);
	List<BusinessTripRequest> findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
			String employeeId, String status, LocalDate endDate, LocalDate startDate);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<BusinessTripRequest> findLockedById(String id);
}
