package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.ClassNoticeRecipient;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ClassNoticeRecipientRepository extends JpaRepository<ClassNoticeRecipient, String> {
	List<ClassNoticeRecipient> findByNoticeIdOrderByStudentId(String noticeId);
	List<ClassNoticeRecipient> findByRecipientUsernameOrderByCreateTimeDesc(String username);
	List<ClassNoticeRecipient> findByNoticeIdAndRecipientUsername(String noticeId, String username);
	long countByNoticeId(String noticeId);
	long countByNoticeIdAndAcknowledgedAtIsNotNull(String noticeId);
}
