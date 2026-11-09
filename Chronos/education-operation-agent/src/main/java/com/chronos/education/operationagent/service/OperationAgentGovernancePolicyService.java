package com.chronos.education.operationagent.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.chronos.education.operationagent.model.OperationAgentCheckpointMode;

/**
 * 以确定性平台规则决定步骤治理方式。LLM 只负责识别目标和组织步骤，不能
 * 降低平台预设的确认或复核要求。
 */
@Service
public class OperationAgentGovernancePolicyService {
	public GovernanceDecision decide(
			OperationAgentToolCatalog.ToolDefinition definition,
			Map<String, Object> config) {
		OperationAgentCheckpointMode mode = definition.checkpointMode();
		boolean dryRun = config != null && Boolean.TRUE.equals(config.get("dryRun"));
		if (dryRun) mode = OperationAgentCheckpointMode.NONE;
		String reason = switch (mode) {
			case NONE -> dryRun
					? "当前步骤只生成候选结果，不写入正式业务数据。"
					: "当前工具通过确定性权限和参数校验后可直接执行。";
			case INITIATOR_CONFIRM -> "正式写入前由任务发起人核对候选结果并确认。";
			case BUSINESS_WORKFLOW -> "该操作由业务流程本身校验任务办理人，不叠加智能体异人审核。";
			case DUAL_CONTROL -> "该操作命中独立复核策略，必须由非发起人审核。";
		};
		return new GovernanceDecision(mode, "OPERATION_AGENT_CHECKPOINT_V2", 2, reason);
	}

	public record GovernanceDecision(
			OperationAgentCheckpointMode mode,
			String policyCode,
			int policyVersion,
			String reason) {
	}
}
