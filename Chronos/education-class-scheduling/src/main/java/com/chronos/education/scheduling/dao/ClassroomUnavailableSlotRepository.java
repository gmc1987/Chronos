package com.chronos.education.scheduling.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.scheduling.model.ClassroomUnavailableSlot;

public interface ClassroomUnavailableSlotRepository extends JpaRepository<ClassroomUnavailableSlot, String> {
	List<ClassroomUnavailableSlot> findBySemesterCodeOrderByClassroomIdAscDayOfWeekAscStartPeriodAsc(
			String semesterCode);
}
