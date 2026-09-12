package com.chronos.education.scheduling.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 隔离自动排课计算线程，防止占满 Web 请求线程或公共异步任务池。 */
@Configuration
public class SchedulingAsyncConfiguration {
	@Bean(name = "scheduleGenerationExecutor")
	public Executor scheduleGenerationExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(20);
		executor.setThreadNamePrefix("schedule-generation-");
		executor.setWaitForTasksToCompleteOnShutdown(false);
		executor.initialize();
		return executor;
	}
}
