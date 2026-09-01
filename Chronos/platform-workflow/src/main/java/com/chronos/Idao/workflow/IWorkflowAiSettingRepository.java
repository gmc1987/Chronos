package com.chronos.Idao.workflow;

import com.chronos.model.workflow.WorkflowAiSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IWorkflowAiSettingRepository extends JpaRepository<WorkflowAiSetting, String> {
}
