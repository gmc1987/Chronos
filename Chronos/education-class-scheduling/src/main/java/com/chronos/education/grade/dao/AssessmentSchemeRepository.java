package com.chronos.education.grade.dao;
import com.chronos.education.grade.model.*; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface AssessmentSchemeRepository extends JpaRepository<AssessmentScheme,String> { List<AssessmentScheme> findByOfferingIdOrderByCreateTimeDesc(String offeringId); }
