package com.chronos.education.scheduling.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.scheduling.model.TeacherTimeConstraint;

public interface TeacherTimeConstraintRepository extends JpaRepository<TeacherTimeConstraint, String> {
	List<TeacherTimeConstraint> findBySemesterCodeAndTeacherId(String semesterCode, String teacherId);
	List<TeacherTimeConstraint> findBySemesterCodeOrderByTeacherIdAscDayOfWeekAscPeriodNoAsc(String semesterCode);
}
