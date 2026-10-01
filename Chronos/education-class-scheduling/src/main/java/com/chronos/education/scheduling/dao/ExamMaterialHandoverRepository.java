package com.chronos.education.scheduling.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.scheduling.model.ExamMaterialHandover;

public interface ExamMaterialHandoverRepository extends JpaRepository<ExamMaterialHandover, String> {
	List<ExamMaterialHandover> findByLedgerIdOrderByHandedAtDesc(String ledgerId);
}
