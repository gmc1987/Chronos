package com.chronos.education.grade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.chronos.education.grade.model.MakeupRetakeRecord;

public final class MakeupRetakeDtos {
    private MakeupRetakeDtos() {}

    public record CreateCommand(String sourceCourseGradeId, String examSessionId, String recordType) {}

    public record View(String id, String sourceCourseGradeId, String studentId, String offeringId,
                       String examSessionId, String recordType, BigDecimal originalScore,
                       BigDecimal resultScore, BigDecimal effectiveScore, String strategy,
                       String status, String sourceSnapshotHash, LocalDateTime publishedAt) {
        public static View of(MakeupRetakeRecord r) {
            return new View(r.getId(), r.getSourceCourseGradeId(), r.getStudentId(), r.getOfferingId(),
                    r.getExamSessionId(), r.getRecordType(), r.getOriginalScore(), r.getResultScore(),
                    r.getEffectiveScore(), r.getStrategy(), r.getStatus(), r.getSourceSnapshotHash(),
                    r.getPublishedAt());
        }
    }
}
