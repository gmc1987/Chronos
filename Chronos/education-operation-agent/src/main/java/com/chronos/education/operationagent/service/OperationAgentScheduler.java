package com.chronos.education.operationagent.service;

import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.chronos.education.operationagent.dao.OperationAgentPlanRepository;

/** 周期扫描只负责触发到期计划；实际运行仍使用同一套快照、检查点和审批机制。 */
@Component
public class OperationAgentScheduler {
	private final OperationAgentPlanRepository plans;
	private final OperationAgentService service;

	public OperationAgentScheduler(
			OperationAgentPlanRepository plans,
			OperationAgentService service) {
		this.plans = plans;
		this.service = service;
	}

	@Scheduled(fixedDelayString = "${chronos.agent.operation.scan-delay-ms:30000}")
	public void triggerDuePlans() {
		for (var plan : plans.findByEnabledTrueAndCycleTypeAndNextScheduledAtLessThanEqual(
				"CRON", LocalDateTime.now())) {
			try {
				service.startScheduled(plan.getId());
			} catch (IllegalStateException ignored) {
				// 多实例竞争时，获得行锁的一方会推进 nextScheduledAt；其余实例跳过本轮。
			}
		}
	}
}
