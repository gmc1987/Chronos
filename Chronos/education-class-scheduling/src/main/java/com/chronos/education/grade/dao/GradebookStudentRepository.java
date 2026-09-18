package com.chronos.education.grade.dao;
import com.chronos.education.grade.model.*; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface GradebookStudentRepository extends JpaRepository<GradebookStudent,String> { List<GradebookStudent> findByGradebookId(String gradebookId); }
