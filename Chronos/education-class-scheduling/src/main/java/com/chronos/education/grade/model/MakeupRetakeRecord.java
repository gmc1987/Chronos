package com.chronos.education.grade.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_makeup_retake_record", uniqueConstraints = @UniqueConstraint(
        name = "uk_edu_makeup_retake_source", columnNames = {"exam_session_id", "exam_candidate_id", "record_type"}))
public class MakeupRetakeRecord extends BaseEntity {
    @Column(name = "source_course_grade_id", nullable = false, length = 64)
    private String sourceCourseGradeId;
    @Column(name = "source_gradebook_id", nullable = false, length = 64)
    private String sourceGradebookId;
    @Column(name = "student_id", nullable = false, length = 64)
    private String studentId;
    @Column(name = "offering_id", nullable = false, length = 64)
    private String offeringId;
    @Column(name = "exam_session_id", nullable = false, length = 64)
    private String examSessionId;
    @Column(name = "exam_candidate_id", nullable = false, length = 64)
    private String examCandidateId;
    @Column(name = "record_type", nullable = false, length = 16)
    private String recordType;
    @Column(name = "result_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal resultScore;
    @Column(name = "max_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxScore;
    @Column(name = "original_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal originalScore;
    @Column(name = "effective_score", precision = 8, scale = 2)
    private BigDecimal effectiveScore;
    @Column(name = "strategy", nullable = false, length = 24)
    private String strategy;
    @Column(name = "status", nullable = false, length = 24)
    private String status = "DRAFT";
    @Column(name = "source_snapshot_hash", nullable = false, length = 64)
    private String sourceSnapshotHash;
    @Column(name = "published_snapshot_json", columnDefinition = "text")
    private String publishedSnapshotJson;
    @Column(name = "submitted_by", length = 128)
    private String submittedBy;
    @Column(name = "approved_by", length = 128)
    private String approvedBy;
    @Column(name = "published_by", length = 128)
    private String publishedBy;
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
    @Column(name = "row_version", nullable = false)
    @Version
    private Long rowVersion = 0L;
}
