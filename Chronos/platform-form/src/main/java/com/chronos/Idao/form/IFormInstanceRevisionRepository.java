package com.chronos.Idao.form;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.model.form.FormInstanceRevision;

public interface IFormInstanceRevisionRepository extends JpaRepository<FormInstanceRevision, String> {
	long countByFormInstanceId(String formInstanceId);

	List<FormInstanceRevision> findByFormInstanceIdOrderByRevisionNoAsc(String formInstanceId);

	List<FormInstanceRevision> findByWorkflowInstanceIdOrderByCreateTimeAsc(String workflowInstanceId);
}
