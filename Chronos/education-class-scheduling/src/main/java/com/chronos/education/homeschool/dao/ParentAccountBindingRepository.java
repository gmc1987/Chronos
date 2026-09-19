package com.chronos.education.homeschool.dao;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.homeschool.model.ParentAccountBinding;
public interface ParentAccountBindingRepository extends JpaRepository<ParentAccountBinding, String> {
	List<ParentAccountBinding> findAllByOrderByCreateTimeDesc();
	Optional<ParentAccountBinding> findByUsernameAndStatus(String username, String status);
	Optional<ParentAccountBinding> findByUsername(String username);
	List<ParentAccountBinding> findByParentIdAndStatus(String parentId, String status);
	List<ParentAccountBinding> findByParentIdInAndStatus(List<String> parentIds, String status);
}
