package com.chronos.education.scheduling.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/** PostgreSQL 学期级事务锁，串行化考试发布与课程资源变更的最终冲突校验。 */
@Service
@RequiredArgsConstructor
public class EducationResourceTransactionLock {
	private final EntityManager entityManager;

	@Transactional(propagation = Propagation.MANDATORY)
	public void lockSemester(String semesterCode) {
		if (semesterCode == null || semesterCode.isBlank()) {
			throw new IllegalArgumentException("学期编码不能为空");
		}
		entityManager.createNativeQuery("""
				SELECT 1 FROM pg_advisory_xact_lock(
				    hashtext('chronos_education_resource'),
				    hashtext(:semesterCode))
				""")
				.setParameter("semesterCode", semesterCode)
				.getSingleResult();
	}
}
