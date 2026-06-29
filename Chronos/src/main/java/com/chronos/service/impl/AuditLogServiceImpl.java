 package com.chronos.service.impl;
 
 import com.chronos.Idao.IAuditLogRepository;
 import com.chronos.model.pojo.AuditLog;
 import com.chronos.service.iService.IAuditLogService;
 import java.time.LocalDateTime;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.stereotype.Service;
 import org.springframework.transaction.annotation.Transactional;
 
 
 
 
 @Service("auditLogService")
 public class AuditLogServiceImpl
   implements IAuditLogService
 {
   @Autowired
   private IAuditLogRepository auditLogRepository;
   
   @Transactional
   public AuditLog log(String username, String action, String detail) {
     AuditLog al = new AuditLog();
     al.setUsername(username);
     al.setAction(action);
     al.setDetail(detail);
     al.setCreateTime(LocalDateTime.now());
     return (AuditLog)this.auditLogRepository.save(al);
   }
 }


