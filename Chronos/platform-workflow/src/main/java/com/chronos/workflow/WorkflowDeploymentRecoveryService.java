package com.chronos.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.chronos.Idao.workflow.IWorkflowDefinitionRepository;
import com.chronos.Idao.workflow.IWorkflowEdgeRepository;
import com.chronos.Idao.workflow.IWorkflowNodeRepository;
import com.chronos.model.workflow.WorkflowDefinition;

/**
 * 修复通过初始化 SQL 导入、但尚未部署到 Flowable 的已发布流程。
 * 每个定义使用独立事务，单个坏模板不会阻止应用启动或其他模板修复。
 */
@Service
public class WorkflowDeploymentRecoveryService {
	private static final Logger log = LoggerFactory.getLogger(WorkflowDeploymentRecoveryService.class);

	private final IWorkflowDefinitionRepository definitions;
	private final IWorkflowNodeRepository nodes;
	private final IWorkflowEdgeRepository edges;
	private final FlowableDeploymentService deployments;
	private final TransactionTemplate transactionTemplate;

	public WorkflowDeploymentRecoveryService(
			IWorkflowDefinitionRepository definitions,
			IWorkflowNodeRepository nodes,
			IWorkflowEdgeRepository edges,
			FlowableDeploymentService deployments,
			TransactionTemplate transactionTemplate) {
		this.definitions = definitions;
		this.nodes = nodes;
		this.edges = edges;
		this.deployments = deployments;
		this.transactionTemplate = transactionTemplate;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void recoverPublishedDefinitions() {
		for (String definitionId : definitions.findPublishedIdsMissingFlowableDeployment()) {
			try {
				transactionTemplate.executeWithoutResult(status -> recover(definitionId));
			} catch (RuntimeException exception) {
				log.error(
						"已发布流程缺少 Flowable 部署且自动修复失败，flowId={}",
						definitionId,
						exception);
			}
		}
	}

	public WorkflowDefinition recover(String definitionId) {
		WorkflowDefinition definition = definitions.findById(definitionId)
				.orElseThrow(() -> new IllegalArgumentException("流程定义不存在"));
		if (!"PUBLISHED".equals(definition.getStatus())) {
			return definition;
		}
		if (definition.getFlowableProcessKey() != null
				&& !definition.getFlowableProcessKey().isBlank()) {
			return definition;
		}
		var deployed = deployments.deploy(
				definition,
				nodes.findByFlowIdOrderByCreateTimeAsc(definitionId),
				edges.findByFlowIdOrderByCreateTimeAsc(definitionId));
		definition.setFlowableDeploymentId(deployed.deploymentId());
		definition.setFlowableProcessKey(deployed.processKey());
		log.info(
				"已修复初始化流程的 Flowable 部署，flowId={}, processKey={}",
				definitionId,
				deployed.processKey());
		return definitions.save(definition);
	}
}
