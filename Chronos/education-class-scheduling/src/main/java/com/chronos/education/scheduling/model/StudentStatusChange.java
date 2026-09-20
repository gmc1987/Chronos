package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 学籍异动申请及审批快照，学生主档只保存当前生效状态。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_student_status_change")
public class StudentStatusChange extends BaseEntity {
	@Column(name = "student_id", nullable = false, length = 64)
	private String studentId;

	@Column(name = "change_type", nullable = false, length = 32)
	private String changeType;

	@Column(name = "from_status", nullable = false, length = 24)
	private String fromStatus;

	@Column(name = "to_status", nullable = false, length = 24)
	private String toStatus;

	@Column(name = "from_grade_id", length = 64)
	private String fromGradeId;

	@Column(name = "to_grade_id", length = 64)
	private String toGradeId;

	@Column(name = "from_major_id", length = 64)
	private String fromMajorId;

	@Column(name = "to_major_id", length = 64)
	private String toMajorId;

	@Column(name = "from_class_id", nullable = false, length = 64)
	private String fromClassId;

	@Column(name = "to_class_id", length = 64)
	private String toClassId;

	@Column(name = "effective_date", nullable = false)
	private LocalDate effectiveDate;

	@Column(name = "reason", nullable = false, length = 1000)
	private String reason;

	@Column(name = "status", nullable = false, length = 24)
	private String status = "PENDING";

	@Column(name = "requested_by", nullable = false, length = 128)
	private String requestedBy;

	@Column(name = "requested_at", nullable = false)
	private LocalDateTime requestedAt;

	@Column(name = "decided_by", length = 128)
	private String decidedBy;

	@Column(name = "decided_at")
	private LocalDateTime decidedAt;

	@Column(name = "decision_comment", length = 1000)
	private String decisionComment;

	@Column(name = "applied_at")
	private LocalDateTime appliedAt;

	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
