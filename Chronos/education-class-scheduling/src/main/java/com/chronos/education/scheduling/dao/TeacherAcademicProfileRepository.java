package com.chronos.education.scheduling.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;

public interface TeacherAcademicProfileRepository extends JpaRepository<TeacherAcademicProfile, String> {
	List<TeacherAcademicProfile> findAllByOrderByTeacherNo();
	Optional<TeacherAcademicProfile> findByEmployeeId(String employeeId);
}
