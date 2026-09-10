package com.chronos.education.scheduling.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;

public interface TeacherAcademicProfileRepository extends JpaRepository<TeacherAcademicProfile, String> {
	List<TeacherAcademicProfile> findAllByOrderByTeacherNo();
	Page<TeacherAcademicProfile> findAllByOrderByTeacherNo(Pageable pageable);
	List<TeacherAcademicProfile> findByIdInOrderByTeacherNo(List<String> ids);
	Page<TeacherAcademicProfile> findByIdInOrderByTeacherNo(
			List<String> ids,
			Pageable pageable);
	Optional<TeacherAcademicProfile> findByEmployeeId(String employeeId);
	List<TeacherAcademicProfile> findByDepartmentIdAndEnabledTrueOrderByTeacherNo(String departmentId);
}
