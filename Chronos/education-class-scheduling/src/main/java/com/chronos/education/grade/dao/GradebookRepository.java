package com.chronos.education.grade.dao;
import com.chronos.education.grade.model.*; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface GradebookRepository extends JpaRepository<Gradebook,String> { List<Gradebook> findByTeacherIdOrderByCreateTimeDesc(String teacherId); Optional<Gradebook> findByOfferingIdAndSchemeId(String offeringId,String schemeId); }
