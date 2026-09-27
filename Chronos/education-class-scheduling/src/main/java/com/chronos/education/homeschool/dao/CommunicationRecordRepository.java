package com.chronos.education.homeschool.dao;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.homeschool.model.CommunicationRecord;
public interface CommunicationRecordRepository extends JpaRepository<CommunicationRecord, String> {
	List<CommunicationRecord> findAllByOrderByOccurredAtDesc();
	List<CommunicationRecord> findByStudentIdInOrderByOccurredAtDesc(Collection<String> studentIds);
}
