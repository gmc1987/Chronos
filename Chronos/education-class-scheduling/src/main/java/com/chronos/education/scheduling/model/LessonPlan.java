package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 教案只关联教学任务，审核结果由 lesson_plan_review 记录，未接 Workflow 时不伪造审批。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_lesson_plan")
public class LessonPlan extends BaseEntity {
	@Column(name = "offering_id", nullable = false, length = 64)
	private String offeringId;
	@Column(name = "schedule_entry_id", length = 64)
	private String scheduleEntryId;
	@Column(nullable = false, length = 200)
	private String title;
	@Column(nullable = false, length = 24)
	private String status = "DRAFT";
	@Column(nullable = false)
	private boolean archived;
}
