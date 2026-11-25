package com.chronos.education.scheduling.service;

import java.time.LocalDate;

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

	/** 同一个场地日期可能落在两个学期内，场地锁让最终占用校验跨学期串行。 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void lockClassroomDate(String classroomId, LocalDate date) {
		entityManager.createNativeQuery("""
				SELECT 1 FROM pg_advisory_xact_lock(
				    hashtext(:classroomId),
				    hashtext(:date))
				""")
				.setParameter("classroomId", "chronos_classroom_reservation:" + classroomId)
				.setParameter("date", date.toString())
				.getSingleResult();
	}
}
