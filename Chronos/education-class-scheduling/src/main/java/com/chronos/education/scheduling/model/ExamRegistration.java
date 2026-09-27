package com.chronos.education.scheduling.model;

import java.time.LocalDateTime;
import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "edu_exam_registration",
	uniqueConstraints = @UniqueConstraint(
			name = "uk_edu_exam_registration",
			columnNames = {"session_id", "student_id"}))
@Getter @Setter
public class ExamRegistration extends BaseEntity {
	@Column(name="plan_id", nullable=false, length=64) private String planId;
	@Column(name="session_id", nullable=false, length=64) private String sessionId;
	@Column(name="student_id", nullable=false, length=64) private String studentId;
	@Column(nullable=false, length=24) private String status = "SUBMITTED";
	@Column(nullable=false, length=24) private String source = "STUDENT";
	@Column(name="submitted_at", nullable=false) private LocalDateTime submittedAt;
	@Column(name="reviewed_at") private LocalDateTime reviewedAt;
	@Column(name="reviewed_by", length=128) private String reviewedBy;
	@Column(length=1000) private String reason;
	@Version @Column(name="row_version", nullable=false) private Long rowVersion = 0L;
}
