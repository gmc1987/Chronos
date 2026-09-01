package com.chronos.Idao.form;

import com.chronos.model.form.FormInstance;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IFormInstanceRepository extends JpaRepository<FormInstance, String> {
	Optional<FormInstance> findByWorkflowInstanceIdAndFormIdAndNodeKey(String workflowInstanceId, String formId,
			String nodeKey);

	List<FormInstance> findByWorkflowInstanceIdOrderByCreateTimeAsc(String workflowInstanceId);
}
