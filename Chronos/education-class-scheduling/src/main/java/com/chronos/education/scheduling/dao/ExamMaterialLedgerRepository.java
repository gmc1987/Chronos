package com.chronos.education.scheduling.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.scheduling.model.ExamMaterialLedger;

public interface ExamMaterialLedgerRepository extends JpaRepository<ExamMaterialLedger, String> {
	List<ExamMaterialLedger> findBySessionIdOrderByCreateTimeDesc(String sessionId);
}
