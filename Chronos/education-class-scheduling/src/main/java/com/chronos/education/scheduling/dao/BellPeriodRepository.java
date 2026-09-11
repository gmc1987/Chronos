package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.BellPeriod;

public interface BellPeriodRepository extends JpaRepository<BellPeriod, String> {
	List<BellPeriod> findByBellScheduleIdOrderByPeriodNo(String bellScheduleId);
	void deleteByBellScheduleId(String bellScheduleId);
}
