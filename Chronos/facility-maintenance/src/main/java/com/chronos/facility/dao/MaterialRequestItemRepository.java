package com.chronos.facility.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.facility.model.MaterialRequestItem;

public interface MaterialRequestItemRepository extends JpaRepository<MaterialRequestItem, String> {
	List<MaterialRequestItem> findByRequestIdOrderByCreateTime(String requestId);
}
