package com.chronos.education.grade.dao;

import com.chronos.education.grade.model.Gradebook;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GradebookRepository extends JpaRepository<Gradebook, String> {
	List<Gradebook> findByTeacherIdOrderByCreateTimeDesc(String teacherId);

	Optional<Gradebook> findByOfferingIdAndSchemeId(String offeringId, String schemeId);

	/**
	 * 成绩更正和补考发布共用成绩册级版本号，发布时必须串行化。
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select gradebook from Gradebook gradebook where gradebook.id = :id")
	Optional<Gradebook> findByIdForUpdate(@Param("id") String id);
}
