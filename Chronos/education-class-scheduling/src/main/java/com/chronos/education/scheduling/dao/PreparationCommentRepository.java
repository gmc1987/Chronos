package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.PreparationComment; import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PreparationCommentRepository extends JpaRepository<PreparationComment,String> { List<PreparationComment> findByPreparationIdOrderByCreateTimeAsc(String id); }
