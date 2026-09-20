package com.chronos.education.scheduling.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 到期任职异动逐条独立执行；职责未交接时保留待生效状态供管理员处理。 */
@Component
public class TeacherEmploymentChangeScheduler {
	private final TeacherEmploymentChangeService service;

	public TeacherEmploymentChangeScheduler(TeacherEmploymentChangeService service) {
		this.service = service;
	}

	@Scheduled(cron = "${chronos.education.teacher-employment-change-cron:0 15 0 * * *}")
	public void applyDueChanges() {
		for (String id : service.dueIds()) {
			try {
				service.apply(id, "SYSTEM");
			} catch (RuntimeException ignored) {
				// 单条交接未完成不能阻塞其他教师异动；记录保持 SCHEDULED，次日继续重试。
			}
		}
	}
}
