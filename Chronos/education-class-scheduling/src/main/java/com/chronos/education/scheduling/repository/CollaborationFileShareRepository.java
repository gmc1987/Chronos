package com.chronos.education.scheduling.repository;

import com.chronos.education.scheduling.model.CollaborationFileShare;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollaborationFileShareRepository extends JpaRepository<CollaborationFileShare, String> {
	List<CollaborationFileShare> findByStatusOrderByCreateTimeDesc(String status);
}
