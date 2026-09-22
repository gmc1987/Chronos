package com.chronos.education.grade.dao;

import com.chronos.education.grade.model.CourseGrade;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseGradeRepository extends JpaRepository<CourseGrade, String> {
	List<CourseGrade> findByGradebookIdAndVersionNo(String gradebookId, Integer versionNo);
	List<CourseGrade> findByStudentIdAndVersionNo(String studentId, Integer versionNo);
	List<CourseGrade> findByStudentIdOrderByVersionNoDesc(String studentId);
	List<CourseGrade> findByGradebookIdAndStudentIdOrderByVersionNoDesc(
		String gradebookId,
		String studentId);
	List<CourseGrade> findAllByGradebookIdIn(Collection<String> gradebookIds);
}
