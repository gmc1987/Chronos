package com.chronos.education.operationagent.dao;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.operationagent.model.OperationAgentEvent;
public interface OperationAgentEventRepository extends JpaRepository<OperationAgentEvent, String> {
	List<OperationAgentEvent> findTop200ByRunIdAndEventSequenceGreaterThanOrderByEventSequence(
			String runId, Long after);
	long countByRunId(String runId);
}
