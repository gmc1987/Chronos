package com.chronos.education.scheduling.dao;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import com.chronos.education.scheduling.model.StudentProfile;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, String> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<StudentProfile> findLockedById(String id);

	List<StudentProfile> findAllByOrderByStudentNo();
	Page<StudentProfile> findAllByOrderByStudentNo(Pageable pageable);

	@Query("""
			select student from StudentProfile student
			where lower(student.studentNo) like concat('%', :keyword, '%')
			   or lower(student.studentName) like concat('%', :keyword, '%')
			order by student.studentNo
			""")
	Page<StudentProfile> searchScheduleOptions(
			@Param("keyword") String keyword,
			Pageable pageable);

	@Query("""
			select student from StudentProfile student
			where (student.administrativeClassId in :classIds or student.gradeId in :gradeIds)
			  and (lower(student.studentNo) like concat('%', :keyword, '%')
			       or lower(student.studentName) like concat('%', :keyword, '%'))
			order by student.studentNo
			""")
	Page<StudentProfile> searchVisibleScheduleOptions(
			@Param("classIds") List<String> classIds,
			@Param("gradeIds") List<String> gradeIds,
			@Param("keyword") String keyword,
			Pageable pageable);

	@Query("""
			select value from StudentProfile value
			where value.administrativeClassId in :classIds or value.gradeId in :gradeIds
			order by value.studentNo
			""")
	List<StudentProfile> findVisible(
			@Param("classIds") List<String> classIds,
			@Param("gradeIds") List<String> gradeIds);

	@Query("""
			select value from StudentProfile value
			where value.administrativeClassId in :classIds or value.gradeId in :gradeIds
			order by value.studentNo
			""")
	Page<StudentProfile> findVisible(
			@Param("classIds") List<String> classIds,
			@Param("gradeIds") List<String> gradeIds,
			Pageable pageable);
	List<StudentProfile> findByAdministrativeClassId(String administrativeClassId);
	long countByMajorId(String majorId);

	long countByGradeId(String gradeId);
	long countByAdministrativeClassId(String administrativeClassId);
}
