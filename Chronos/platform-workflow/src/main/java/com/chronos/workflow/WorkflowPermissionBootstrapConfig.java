package com.chronos.workflow;

import com.chronos.Idao.workflow.*;
import com.chronos.model.workflow.*;
import java.util.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class WorkflowPermissionBootstrapConfig {
	@Bean
	ApplicationRunner workflowPermissionBootstrap(IWorkflowDefinitionRepository definitions,
			IWorkflowDefinitionAclRepository acls, IWorkflowInstanceRepository instances,
			IWorkflowInstanceParticipantRepository participants, IWorkflowTaskRepository tasks,
			IWorkflowTaskCandidateRepository candidates) {
		return args -> backfill(definitions, acls, instances, participants, tasks, candidates);
	}

	@Transactional
	void backfill(IWorkflowDefinitionRepository definitions, IWorkflowDefinitionAclRepository acls,
			IWorkflowInstanceRepository instances, IWorkflowInstanceParticipantRepository participants,
			IWorkflowTaskRepository tasks, IWorkflowTaskCandidateRepository candidates) {
		for (WorkflowDefinition d : definitions.findAll()) {
			List<WorkflowDefinitionAcl> existing = acls
					.findByDefinitionIdOrderByActionAscSubjectTypeAscSubjectIdAsc(d.getId());
			if (existing.stream().noneMatch(a -> "MANAGE".equals(a.getAction()))) {
				String manager = d.getManagerUser() != null && !d.getManagerUser().isBlank() ? d.getManagerUser()
						: d.getCreateBy();
				if (manager != null && !manager.isBlank()) {
					WorkflowDefinitionAcl a = new WorkflowDefinitionAcl();
					a.setDefinitionId(d.getId());
					a.setSubjectType("USER");
					a.setSubjectId(manager);
					a.setAction("MANAGE");
					a.setEnabled(true);
					acls.save(a);
				}
			}
		}
		for (WorkflowInstance i : instances.findAll())
			ensureParticipant(participants, i.getId(), i.getInitiator(), "INITIATOR", null);
		for (WorkflowTask t : tasks.findAll()) {
			String type = "CC".equals(t.getStatus()) ? "CC" : "ASSIGNEE";
			ensureParticipant(participants, t.getInstanceId(), t.getAssignee(), type, t.getId());
			if (!"CC".equals(type) && t.getAssignee() != null && !t.getAssignee().isBlank()
					&& candidates.findByTaskId(t.getId()).isEmpty()) {
				WorkflowTaskCandidate c = new WorkflowTaskCandidate();
				c.setTaskId(t.getId());
				c.setSubjectType("USER");
				c.setSubjectId(t.getAssignee());
				candidates.save(c);
			}
		}
	}

	private void ensureParticipant(IWorkflowInstanceParticipantRepository repo, String instance, String user,
			String type, String task) {
		if (user == null || user.isBlank()
				|| repo.existsByInstanceIdAndUsernameAndParticipantTypeAndActiveTrue(instance, user, type))
			return;
		WorkflowInstanceParticipant p = new WorkflowInstanceParticipant();
		p.setInstanceId(instance);
		p.setUsername(user);
		p.setParticipantType(type);
		p.setSourceTaskId(task);
		p.setActive(true);
		repo.save(p);
	}
}
