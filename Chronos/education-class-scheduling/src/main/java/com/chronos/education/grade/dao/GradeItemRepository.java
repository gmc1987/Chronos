package com.chronos.education.grade.dao;
import com.chronos.education.grade.model.*; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface GradeItemRepository extends JpaRepository<GradeItem,String> { List<GradeItem> findByGradebookId(String gradebookId); void deleteByGradebookId(String gradebookId); }
