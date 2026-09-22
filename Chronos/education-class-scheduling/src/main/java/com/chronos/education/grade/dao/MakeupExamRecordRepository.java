package com.chronos.education.grade.dao;

import com.chronos.education.grade.model.MakeupExamRecord;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MakeupExamRecordRepository extends JpaRepository<MakeupExamRecord, String> {
	List<MakeupExamRecord> findByGradebookIdOrderByCreateTimeDesc(String gradebookId);
	boolean existsBySourceGradeIdAndAttemptType(String sourceGradeId, String attemptType);

	/** 防止同一补考成绩被并发发布两次。 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select record from MakeupExamRecord record where record.id = :id")
	Optional<MakeupExamRecord> findByIdForUpdate(@Param("id") String id);
}
