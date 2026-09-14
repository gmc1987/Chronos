package com.chronos.education.scheduling.model;

import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
	private String status = "DRAFT";

	@Column
	private Integer score;

	@Column(name = "teacher_feedback", columnDefinition = "text")
	private String teacherFeedback;

	@Column(name = "submitted_at")
	private LocalDateTime submittedAt;

	@Column(name = "graded_at")
	private LocalDateTime gradedAt;
}
