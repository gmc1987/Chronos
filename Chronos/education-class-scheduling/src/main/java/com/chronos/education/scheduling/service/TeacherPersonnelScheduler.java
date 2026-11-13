package com.chronos.education.scheduling.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class TeacherPersonnelScheduler {
	private final TeacherPersonnelWorkflowService personnel;

	@Scheduled(fixedDelayString = "${chronos.education.teacher-personnel-delay-ms:15000}")
	public void applyApprovedCases() {
		for (String id : personnel.due()) apply(id);
	}

	public void apply(String id) {
		try {
			personnel.apply(id);
		} catch (RuntimeException exception) {
			log.warn("教师人事申请生效失败, instanceId={}", id, exception);
			String message = exception instanceof IllegalArgumentException || exception instanceof IllegalStateException
					? exception.getMessage() : "生效失败，请检查关联记录或联系系统管理员";
			personnel.failed(id, message);
		}
	}
}
