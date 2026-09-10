package com.chronos.service.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.Idao.IAuditLogRepository;
import com.chronos.model.pojo.AuditLog;
import com.chronos.service.iService.IAuditLogService;

@Service("auditLogService")
public class AuditLogServiceImpl implements IAuditLogService {
	private final IAuditLogRepository auditLogRepository;

	public AuditLogServiceImpl(IAuditLogRepository auditLogRepository) {
		this.auditLogRepository = auditLogRepository;
	}

	@Transactional
	@Override
	public AuditLog log(String username, String action, String detail) {
		AuditLog auditLog = new AuditLog();
		auditLog.setUsername(username);
		auditLog.setAction(action);
		auditLog.setDetail(detail);
		auditLog.setCreateTime(LocalDateTime.now());
		return auditLogRepository.save(auditLog);
	}
}
