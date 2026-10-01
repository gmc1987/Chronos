package com.chronos.education.homeschool.dao;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.homeschool.model.HomeNoticeTarget;
public interface HomeNoticeTargetRepository extends JpaRepository<HomeNoticeTarget, String> {
	List<HomeNoticeTarget> findByNoticeIdOrderByCreateTime(String noticeId);
	List<HomeNoticeTarget> findByParentIdOrderByCreateTimeDesc(String parentId);
	Optional<HomeNoticeTarget> findByNoticeIdAndStudentIdAndParentId(String noticeId, String studentId, String parentId);
}
