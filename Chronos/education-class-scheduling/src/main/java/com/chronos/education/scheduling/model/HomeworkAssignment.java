package com.chronos.education.scheduling.model;

import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_homework_assignment", indexes = {
		@Index(name = "idx_edu_homework_assignment_offering", columnList = "offering_id,status") })
public class HomeworkAssignment extends BaseEntity {
	@Column(name = "offering_id", nullable = false, length = 64)
	private String offeringId;

	@Column(name = "teaching_plan_item_id", length = 64)
	private String teachingPlanItemId;

	@Column(name = "preparation_id", length = 64)
	private String preparationId;

	@Column(name = "lesson_plan_id", length = 64)
	private String lessonPlanId;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(name = "question_snapshot_json", nullable = false, columnDefinition = "text")
	private String questionSnapshotJson = "[]";

	@Column(name = "instructions_json", columnDefinition = "text")
	private String instructionsJson;

	@Column(name = "due_at")
	private LocalDateTime dueAt;

	@Column(name = "max_score", nullable = false)
	private Integer maxScore = 100;

	@Column(name = "allow_late", nullable = false)
	private boolean allowLate;

	@Column(nullable = false, length = 24)
	private String status = "DRAFT";
}
