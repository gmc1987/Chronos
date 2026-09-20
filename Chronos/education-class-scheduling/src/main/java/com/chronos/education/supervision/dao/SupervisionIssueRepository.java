package com.chronos.education.supervision.dao;
import com.chronos.education.supervision.model.SupervisionIssue;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SupervisionIssueRepository extends JpaRepository<SupervisionIssue, String> {
	List<SupervisionIssue> findByStatusAndDueAtBefore(String status, LocalDateTime time);
}
