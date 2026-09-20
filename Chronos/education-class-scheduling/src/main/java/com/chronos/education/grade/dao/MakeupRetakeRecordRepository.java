package com.chronos.education.grade.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.grade.model.MakeupRetakeRecord;

public interface MakeupRetakeRecordRepository extends JpaRepository<MakeupRetakeRecord, String> {
    Optional<MakeupRetakeRecord> findByExamSessionIdAndExamCandidateIdAndRecordType(
            String examSessionId, String examCandidateId, String recordType);
    List<MakeupRetakeRecord> findByStudentIdAndStatusOrderByPublishedAtDesc(String studentId, String status);
}
