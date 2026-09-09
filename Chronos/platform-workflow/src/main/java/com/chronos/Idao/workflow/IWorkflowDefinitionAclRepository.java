package com.chronos.Idao.workflow;
import com.chronos.model.workflow.WorkflowDefinitionAcl;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface IWorkflowDefinitionAclRepository extends JpaRepository<WorkflowDefinitionAcl,String>{
    List<WorkflowDefinitionAcl> findByDefinitionIdOrderByActionAscSubjectTypeAscSubjectIdAsc(String definitionId);
    List<WorkflowDefinitionAcl> findByDefinitionIdAndActionAndEnabledTrue(String definitionId,String action);
    void deleteByDefinitionId(String definitionId);
}
