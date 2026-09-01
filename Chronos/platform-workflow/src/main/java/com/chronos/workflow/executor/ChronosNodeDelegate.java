package com.chronos.workflow.executor;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("chronosNodeDelegate")
public class ChronosNodeDelegate implements JavaDelegate {
	@Override
	public void execute(DelegateExecution execution) {
		throw new IllegalStateException("自动节点执行器尚未启用：" + execution.getCurrentActivityId());
	}
}
