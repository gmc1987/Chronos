package com.chronos.facility.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.facility.model.RepairOrderEvent;

public interface RepairOrderEventRepository extends JpaRepository<RepairOrderEvent, String> {
	List<RepairOrderEvent> findByRepairOrderIdOrderByCreateTime(String repairOrderId);
}
