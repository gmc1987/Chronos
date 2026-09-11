package com.chronos.education.scheduling.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;

public interface TeacherAcademicProfileRepository extends JpaRepository<TeacherAcademicProfile, String> {
	List<TeacherAcademicProfile> findAllByOrderByTeacherNo();
	Page<TeacherAcademicProfile> findAllByOrderByTeacherNo(Pageable pageable);
	List<TeacherAcademicProfile> findByIdInOrderByTeacherNo(List<String> ids);
	Page<TeacherAcademicProfile> findByIdInOrderByTeacherNo(
			List<String> ids,
			Pageable pageable);
	Optional<TeacherAcademicProfile> findByEmployeeId(String employeeId);
	@Query("""
			select profile from TeacherAcademicProfile profile
			where profile.id in :ids or profile.employeeId in :ids
			""")
	List<TeacherAcademicProfile> findByIdInOrEmployeeIdIn(@Param("ids") List<String> ids);
	List<TeacherAcademicProfile> findByDepartmentIdAndEnabledTrueOrderByTeacherNo(String departmentId);
}
