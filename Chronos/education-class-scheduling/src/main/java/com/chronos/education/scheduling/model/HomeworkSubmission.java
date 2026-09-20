package com.chronos.education.scheduling.model;

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
@Table(name = "edu_homework_submission",
		uniqueConstraints = @UniqueConstraint(name = "uk_edu_homework_submission_student",
				columnNames = { "assignment_id", "student_id" }))
public class HomeworkSubmission extends BaseEntity {
	@Column(name = "assignment_id", nullable = false, length = 64)
	private String assignmentId;

	@Column(name = "student_id", nullable = false, length = 64)
	private String studentId;

	@Column(name = "answer_snapshot_json", nullable = false, columnDefinition = "text")
	private String answerSnapshotJson = "{}";

	@Column(nullable = false, length = 24)
	private String status = "NOT_STARTED";

	@Column(name = "attempt_no", nullable = false)
	private Integer attemptNo = 1;

	@Column
	private Integer score;

	@Column(name = "teacher_feedback", columnDefinition = "text")
	private String teacherFeedback;

	@Column(name = "question_scores_json", nullable = false, columnDefinition = "text")
	private String questionScoresJson = "{}";

	@Column(name = "annotation_snapshot_json", nullable = false, columnDefinition = "text")
	private String annotationSnapshotJson = "[]";

	@Column(name = "attachment_snapshot_json", nullable = false, columnDefinition = "text")
	private String attachmentSnapshotJson = "[]";

	@Column(name = "grades_published", nullable = false)
	private boolean gradesPublished;

	@Column(name = "submitted_at")
	private LocalDateTime submittedAt;

	@Column(name = "graded_at")
	private LocalDateTime gradedAt;

	/** 防止学生提交与教师评分、成绩发布同时写入造成状态或答案丢失。 */
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
