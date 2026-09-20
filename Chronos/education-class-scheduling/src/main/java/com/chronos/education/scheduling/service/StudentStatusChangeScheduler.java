package com.chronos.education.scheduling.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 到期学籍异动执行器；每条记录独立事务，单条失败不会阻断同批其他学生。 */
@Component
public class StudentStatusChangeScheduler {
	private static final Logger LOG = LoggerFactory.getLogger(StudentStatusChangeScheduler.class);
	private final StudentStatusChangeService changes;

	public StudentStatusChangeScheduler(StudentStatusChangeService changes) {
		this.changes = changes;
	}

	@Scheduled(cron = "${chronos.education.student-status-change-cron:0 5 0 * * *}")
	public void applyDueChanges() {
		for (String id : changes.dueChangeIds()) {
			try {
				changes.applyDue(id);
			} catch (RuntimeException exception) {
				// 保留 APPROVED_PENDING，修复主数据后下一次调度可自动重试。
				LOG.error("到期学籍异动生效失败, changeId={}", id, exception);
			}
		}
	}
}
