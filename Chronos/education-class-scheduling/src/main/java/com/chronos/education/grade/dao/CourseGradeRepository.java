package com.chronos.education.grade.dao;
import com.chronos.education.grade.model.*; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface CourseGradeRepository extends JpaRepository<CourseGrade,String> { List<CourseGrade> findByGradebookIdAndVersionNo(String gradebookId,Integer versionNo); List<CourseGrade> findByStudentIdAndVersionNo(String studentId,Integer versionNo); List<CourseGrade> findByStudentIdOrderByVersionNoDesc(String studentId); List<CourseGrade> findByGradebookIdOrderByVersionNoDesc(String gradebookId); }
