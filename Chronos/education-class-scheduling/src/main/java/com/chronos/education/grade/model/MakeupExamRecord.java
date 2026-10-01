package com.chronos.education.grade.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 补考或重修登记；发布结果同样创建新的课程成绩版本。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
	name = "edu_makeup_exam_record",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_makeup_source_type",
		columnNames = { "source_grade_id", "attempt_type" }))
public class MakeupExamRecord extends BaseEntity {
	@Column(name = "gradebook_id", nullable = false, length = 64)
	private String gradebookId;
	@Column(name = "source_grade_id", nullable = false, length = 64)
	private String sourceGradeId;
	@Column(name = "student_id", nullable = false, length = 64)
	private String studentId;
	@Column(name = "attempt_type", nullable = false, length = 16)
	private String attemptType;
	@Column(name = "result_score", precision = 8, scale = 2)
	private BigDecimal resultScore;
	@Column(nullable = false, length = 24)
	private String status = "REGISTERED";
	@Column(name = "published_grade_id", length = 64)
	private String publishedGradeId;
	@Column(length = 500)
	private String remark;
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
