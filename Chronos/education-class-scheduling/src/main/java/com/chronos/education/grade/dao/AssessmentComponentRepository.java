package com.chronos.education.grade.dao;
import com.chronos.education.grade.model.*; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface AssessmentComponentRepository extends JpaRepository<AssessmentComponent,String> { List<AssessmentComponent> findBySchemeIdOrderBySortOrder(String schemeId); }
