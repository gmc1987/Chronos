package com.chronos.Idao.workflow;

import com.chronos.model.workflow.WorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface IWorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, String> {
	boolean existsByFlowCodeAndVersion(String flowCode, String version);
	List<WorkflowDefinition> findByStatusOrderByFlowNameAsc(String status);
	List<WorkflowDefinition> findByFlowCodeAndStatusOrderByCreateTimeDesc(String flowCode, String status);

	/** 只投影 ID，避免事务外读取 PostgreSQL OID 类型的配置字段。 */
	@Query("""
			select definition.id
			from WorkflowDefinition definition
			where definition.status = 'PUBLISHED'
			  and (definition.flowableProcessKey is null or definition.flowableProcessKey = '')
			order by definition.flowName
			""")
	List<String> findPublishedIdsMissingFlowableDeployment();
}
