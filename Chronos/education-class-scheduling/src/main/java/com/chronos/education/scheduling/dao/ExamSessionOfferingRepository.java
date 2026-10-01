package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.ExamSessionOffering;

public interface ExamSessionOfferingRepository extends JpaRepository<ExamSessionOffering, String> {
	List<ExamSessionOffering> findBySessionId(String sessionId);
	void deleteBySessionId(String sessionId);
}
