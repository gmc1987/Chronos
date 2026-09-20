package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.ClassNotice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ClassNoticeRepository extends JpaRepository<ClassNotice, String> {
	List<ClassNotice> findByClassIdOrderByCreateTimeDesc(String classId);
}
