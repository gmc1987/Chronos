package com.chronos.education.homeschool.dao;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.homeschool.model.ParentFeedback;
public interface ParentFeedbackRepository extends JpaRepository<ParentFeedback, String> {
	List<ParentFeedback> findByParentIdOrderByCreateTimeDesc(String parentId);
	List<ParentFeedback> findAllByOrderByCreateTimeDesc();
}
