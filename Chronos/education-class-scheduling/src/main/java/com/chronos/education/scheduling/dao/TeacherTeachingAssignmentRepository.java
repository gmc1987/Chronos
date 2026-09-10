package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.chronos.education.scheduling.model.TeacherTeachingAssignment;

public interface TeacherTeachingAssignmentRepository
		extends JpaRepository<TeacherTeachingAssignment, String> {
	List<TeacherTeachingAssignment> findAllByOrderByCreateTimeDesc();
	Page<TeacherTeachingAssignment> findAllByOrderByCreateTimeDesc(Pageable pageable);
	List<TeacherTeachingAssignment> findByTeacherIdAndEnabledTrue(String teacherId);
	List<TeacherTeachingAssignment> findByTeacherIdInAndEnabledTrue(List<String> teacherIds);

	@Query("""
			select assignment from TeacherTeachingAssignment assignment
			where assignment.teacherId in :teacherIds
			   or assignment.administrativeClassId in :classIds
			   or assignment.gradeId in :gradeIds
			order by assignment.createTime desc
			""")
	List<TeacherTeachingAssignment> findVisible(
			List<String> teacherIds,
			List<String> classIds,
			List<String> gradeIds);

	@Query("""
			select assignment from TeacherTeachingAssignment assignment
			where assignment.teacherId in :teacherIds
			   or assignment.administrativeClassId in :classIds
			   or assignment.gradeId in :gradeIds
			""")
	Page<TeacherTeachingAssignment> findVisible(
			List<String> teacherIds,
			List<String> classIds,
			List<String> gradeIds,
			Pageable pageable);

	long countByTeacherId(String teacherId);

	long countBySubjectId(String subjectId);

	long countByGradeId(String gradeId);
}
