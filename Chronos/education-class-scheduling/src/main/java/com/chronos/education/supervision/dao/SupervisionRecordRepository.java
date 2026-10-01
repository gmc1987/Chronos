package com.chronos.education.supervision.dao;
import com.chronos.education.supervision.model.SupervisionRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SupervisionRecordRepository extends JpaRepository<SupervisionRecord, String> {
	Optional<SupervisionRecord> findByAssignmentId(String assignmentId);
}
