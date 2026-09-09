package com.chronos.Idao.workflow;
import com.chronos.model.workflow.WorkflowInstanceParticipant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface IWorkflowInstanceParticipantRepository extends JpaRepository<WorkflowInstanceParticipant,String>{
    boolean existsByInstanceIdAndUsernameAndActiveTrue(String instanceId,String username);
    boolean existsByInstanceIdAndUsernameAndParticipantTypeAndActiveTrue(String instanceId,String username,String participantType);
    List<WorkflowInstanceParticipant> findByInstanceIdOrderByCreateTimeAsc(String instanceId);
    List<WorkflowInstanceParticipant> findByUsernameAndActiveTrueOrderByCreateTimeDesc(String username);
    void deleteByInstanceId(String instanceId);
}
