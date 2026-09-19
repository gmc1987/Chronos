package com.chronos.education.supervision.dao;
import com.chronos.education.supervision.model.SupervisionRectification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SupervisionRectificationRepository extends JpaRepository<SupervisionRectification, String> {
	Optional<SupervisionRectification> findByIssueId(String issueId);
}
