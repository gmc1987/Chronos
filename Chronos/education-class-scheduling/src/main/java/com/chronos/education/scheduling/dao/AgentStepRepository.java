package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.AgentStep;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentStepRepository extends JpaRepository<AgentStep, String> {
	List<AgentStep> findByRunIdOrderByStepNo(String runId);

	Optional<AgentStep> findByRunIdAndStepKey(String runId, String stepKey);
}
