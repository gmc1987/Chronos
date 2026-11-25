package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.ClassroomReservation;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassroomReservationRepository
		extends JpaRepository<ClassroomReservation, String> {
	Optional<ClassroomReservation> findByWorkflowInstanceId(String workflowInstanceId);

	List<ClassroomReservation> findByApplicantUsernameOrderByUsageDateDesc(
			String applicantUsername);

	List<ClassroomReservation> findByUsageDateBetweenOrderByUsageDateAscStartPeriodAsc(
			LocalDate startDate,
			LocalDate endDate);

	@Query("""
			select reservation from ClassroomReservation reservation
			where reservation.usageDate between :from and :through
			  and (:status is null or reservation.status = :status)
			  and exists (select room.id from Classroom room
			              where room.id = reservation.classroomId and room.campusId in :campuses)
			order by reservation.usageDate, reservation.startPeriod, reservation.id
			""")
	Page<ClassroomReservation> findForCampuses(Set<String> campuses, LocalDate from,
			LocalDate through, String status, Pageable page);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select reservation from ClassroomReservation reservation where reservation.id = :id")
	Optional<ClassroomReservation> findLockedById(@Param("id") String id);

	@Query("""
			select reservation from ClassroomReservation reservation
			where reservation.classroomId = :classroomId
			  and reservation.usageDate = :usageDate
			  and reservation.status = 'ACTIVE'
			  and reservation.startPeriod <= :endPeriod
			  and (reservation.startPeriod + reservation.durationPeriods - 1) >= :startPeriod
			  and (:excludedId is null or reservation.id <> :excludedId)
			""")
	List<ClassroomReservation> findActiveOverlapping(
			@Param("classroomId") String classroomId,
			@Param("usageDate") LocalDate usageDate,
			@Param("startPeriod") Integer startPeriod,
			@Param("endPeriod") Integer endPeriod,
			@Param("excludedId") String excludedId);
}
