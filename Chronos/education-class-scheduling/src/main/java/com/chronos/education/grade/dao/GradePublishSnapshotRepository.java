package com.chronos.education.grade.dao;
import com.chronos.education.grade.model.*; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface GradePublishSnapshotRepository extends JpaRepository<GradePublishSnapshot,String> { Optional<GradePublishSnapshot> findByGradebookIdAndVersionNo(String gradebookId,Integer versionNo); }

