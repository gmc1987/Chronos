package com.chronos.education.scheduling.dao;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.scheduling.model.StudentProfile;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, String> {
	List<StudentProfile> findAllByOrderByStudentNo();
	Page<StudentProfile> findAllByOrderByStudentNo(Pageable pageable);
	List<StudentProfile> findByAdministrativeClassId(String administrativeClassId);
	long countByMajorId(String majorId);

	long countByGradeId(String gradeId);
	long countByAdministrativeClassId(String administrativeClassId);
}
