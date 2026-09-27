package com.chronos.Idao.form;

import com.chronos.model.form.FormInstance;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface IFormInstanceRepository extends JpaRepository<FormInstance, String> {
	Optional<FormInstance> findByWorkflowInstanceIdAndFormIdAndNodeKey(String workflowInstanceId, String formId,
			String nodeKey);

	/** Serialize edits of an existing form so revision numbers remain monotonic. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select form from FormInstance form where form.workflowInstanceId = :workflowInstanceId and form.formId = :formId and form.nodeKey = :nodeKey")
	Optional<FormInstance> findLockedForUpdate(
			@Param("workflowInstanceId") String workflowInstanceId,
			@Param("formId") String formId,
			@Param("nodeKey") String nodeKey);

	List<FormInstance> findByWorkflowInstanceIdOrderByCreateTimeAsc(String workflowInstanceId);
}
