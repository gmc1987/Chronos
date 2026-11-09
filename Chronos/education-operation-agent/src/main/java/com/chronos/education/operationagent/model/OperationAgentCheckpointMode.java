package com.chronos.education.operationagent.model;

/**
 * 智能体步骤的人工治理方式。
 *
 * <p>风险等级只描述业务影响，不直接决定由谁确认。检查点模式由平台策略
 * 确定，模型和前端都不能把需要异人复核的步骤降级成本人确认。</p>
 */
public enum OperationAgentCheckpointMode {
	NONE,
	INITIATOR_CONFIRM,
	BUSINESS_WORKFLOW,
	DUAL_CONTROL;

	public boolean requiresCheckpoint() {
		return this == INITIATOR_CONFIRM || this == DUAL_CONTROL;
	}
}
