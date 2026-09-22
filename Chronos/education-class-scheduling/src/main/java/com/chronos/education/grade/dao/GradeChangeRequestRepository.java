package com.chronos.education.grade.dao;

import com.chronos.education.grade.model.GradeChangeRequest;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GradeChangeRequestRepository extends JpaRepository<GradeChangeRequest, String> {
	List<GradeChangeRequest> findByGradebookIdOrderByCreateTimeDesc(String gradebookId);
	boolean existsByCourseGradeIdAndStatus(String courseGradeId, String status);

	/** 专用审核回写锁，避免专用接口与工作流完成事件重复生成成绩版本。 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select request from GradeChangeRequest request where request.id = :id")
	Optional<GradeChangeRequest> findByIdForUpdate(@Param("id") String id);
}
