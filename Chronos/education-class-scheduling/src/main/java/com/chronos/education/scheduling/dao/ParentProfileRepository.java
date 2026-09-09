package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.ParentProfile;

public interface ParentProfileRepository extends JpaRepository<ParentProfile, String> {
	List<ParentProfile> findAllByOrderByParentNo();
	Page<ParentProfile> findAllByOrderByParentNo(Pageable pageable);
}
