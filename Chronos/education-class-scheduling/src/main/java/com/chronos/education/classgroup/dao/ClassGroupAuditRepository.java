package com.chronos.education.classgroup.dao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.classgroup.model.ClassGroupAudit;

public interface ClassGroupAuditRepository extends JpaRepository<ClassGroupAudit, String> {
	Page<ClassGroupAudit> findByGroupIdOrderByCreateTimeDesc(String groupId, Pageable pageable);
}
