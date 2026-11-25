package com.chronos.service.iService;

import com.chronos.model.pojo.AuditLog;

public interface IAuditLogService {
	AuditLog log(String username, String action, String detail);

	AuditLog log(String username, String action, String detail, String organizationId);
}
