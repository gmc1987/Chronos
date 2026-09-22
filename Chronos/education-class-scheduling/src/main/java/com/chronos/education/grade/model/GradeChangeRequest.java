package com.chronos.education.grade.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 已发布成绩更正单；审批通过后创建新的 CourseGrade 版本，不修改原始版本。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_grade_change_request")
public class GradeChangeRequest extends BaseEntity {
	@Column(name = "gradebook_id", nullable = false, length = 64)
	private String gradebookId;
	@Column(name = "course_grade_id", nullable = false, length = 64)
	private String courseGradeId;
	@Column(name = "student_id", nullable = false, length = 64)
	private String studentId;
	@Column(name = "before_score", nullable = false, precision = 8, scale = 2)
	private BigDecimal beforeScore;
	@Column(name = "after_score", nullable = false, precision = 8, scale = 2)
	private BigDecimal afterScore;
	@Column(nullable = false, length = 1000)
	private String reason;
	@Column(name = "workflow_instance_id", length = 64)
	private String workflowInstanceId;
	@Column(nullable = false, length = 24)
	private String status = "REVIEWING";
	@Column(name = "approved_by", length = 128)
	private String approvedBy;
	@Column(name = "approved_at")
	private LocalDateTime approvedAt;
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
