package com.chronos.service.iService;

import com.chronos.model.pojo.AuditLog;

public interface IAuditLogService {
  AuditLog log(String paramString1, String paramString2, String paramString3);
}


