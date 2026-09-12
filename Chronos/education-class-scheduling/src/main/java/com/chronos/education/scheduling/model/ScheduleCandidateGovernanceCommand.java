package com.chronos.education.scheduling.model;

/** 候选方案负责人及协作备注更新命令。 */
public record ScheduleCandidateGovernanceCommand(
		String ownerUsername,
		String remark) {
}
