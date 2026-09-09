package com.chronos.Idao.workflow;
import com.chronos.model.workflow.WorkflowTaskCandidate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface IWorkflowTaskCandidateRepository extends JpaRepository<WorkflowTaskCandidate,String>{
    List<WorkflowTaskCandidate> findByTaskId(String taskId);
    List<WorkflowTaskCandidate> findBySubjectTypeAndSubjectId(String subjectType,String subjectId);
    boolean existsByTaskIdAndSubjectTypeAndSubjectId(String taskId,String subjectType,String subjectId);
    void deleteByTaskId(String taskId);
}
