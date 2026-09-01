package com.chronos.Idao;

import com.chronos.model.pojo.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("auditLogRepository")
public interface IAuditLogRepository extends JpaRepository<AuditLog, String> {}

