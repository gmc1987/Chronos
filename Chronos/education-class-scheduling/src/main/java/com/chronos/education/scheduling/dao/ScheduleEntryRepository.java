package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chronos.education.scheduling.model.ScheduleEntry;

public interface ScheduleEntryRepository extends JpaRepository<ScheduleEntry, String> {
	List<ScheduleEntry> findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(String semesterCode);

	List<ScheduleEntry> findByOfferingId(String offeringId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from ScheduleEntry entry where entry.semesterCode = :semesterCode")
	int deleteAllForRollback(@Param("semesterCode") String semesterCode);

	@Query("""
			select entry from ScheduleEntry entry, CourseOffering offering
			where entry.offeringId = offering.id
			  and offering.teacherId in (:teacherProfileId, :employeeId)
			  and entry.semesterCode = :semesterCode
			  and entry.status <> 'CANCELLED'
			order by entry.dayOfWeek, entry.periodNo
			""")
	List<ScheduleEntry> findTeacherSchedule(
			@Param("teacherProfileId") String teacherProfileId,
			@Param("employeeId") String employeeId,
			@Param("semesterCode") String semesterCode);

	@Query("""
			select entry from ScheduleEntry entry
			where entry.semesterCode = :semester
			  and entry.dayOfWeek = :day
			  and entry.periodNo <= :endPeriod
			  and (entry.periodNo + entry.durationPeriods - 1) >= :startPeriod
			  and entry.startWeek <= :endWeek
			  and entry.endWeek >= :startWeek
			  and (:excludedId is null or entry.id <> :excludedId)
			""")
	List<ScheduleEntry> findOverlapping(
			@Param("semester") String semester,
			@Param("day") Integer day,
			@Param("startPeriod") Integer startPeriod,
			@Param("endPeriod") Integer endPeriod,
			@Param("startWeek") Integer startWeek,
			@Param("endWeek") Integer endWeek,
			@Param("excludedId") String excludedId);

	long countByOfferingId(String offeringId);

	long countByClassroomId(String classroomId);
}
