package com.chronos.education.scheduling.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.ParentProfile;

public interface ParentProfileRepository extends JpaRepository<ParentProfile, String> {
	Optional<ParentProfile> findFirstByPhoneAndStatusOrderByCreateTime(String phone, String status);
	boolean existsByParentNo(String parentNo);

	List<ParentProfile> findAllByOrderByParentNo();
	Page<ParentProfile> findAllByOrderByParentNo(Pageable pageable);
}
