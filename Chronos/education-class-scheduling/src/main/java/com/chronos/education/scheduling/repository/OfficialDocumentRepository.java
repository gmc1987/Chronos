package com.chronos.education.scheduling.repository;

import com.chronos.education.scheduling.model.OfficialDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfficialDocumentRepository extends JpaRepository<OfficialDocument, String> {
	List<OfficialDocument> findByDrafterUsernameOrderByCreateTimeDesc(String username);
	List<OfficialDocument> findByStatusOrderByCreateTimeDesc(String status);
	Optional<OfficialDocument> findByWorkflowInstanceId(String workflowInstanceId);
}
