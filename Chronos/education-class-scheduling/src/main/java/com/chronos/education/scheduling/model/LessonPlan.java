package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
	@Column(name = "school_id", nullable = false, length = 64)
	private String schoolId = "LEGACY";
	@Column(name = "campus_id", length = 64)
	private String campusId;
	@Column(name = "owner_teacher_id", length = 64)
	private String ownerTeacherId;
	@Column(name = "offering_id", nullable = false, length = 64)
	private String offeringId;
	@Column(name = "schedule_entry_id", length = 64)
	private String scheduleEntryId;
	@Column(name = "teaching_plan_id", length = 64)
	private String teachingPlanId;
	@Column(name = "plan_item_id", length = 64)
	private String planItemId;
	@Column(name = "lesson_no", nullable = false)
	private Integer lessonNo;
	@Column(name = "teaching_week", nullable = false)
	private Integer teachingWeek;
	@Column(name = "lesson_hours", nullable = false)
	private Integer lessonHours;
	@Column(name = "lesson_type", nullable = false, length = 24)
	private String lessonType;
	@Column(nullable = false, length = 200)
	private String title;
	@Column(nullable = false, length = 24)
	private String status = "DRAFT";
	@Column(columnDefinition = "text")
	private String objectives;
	@Column(name = "key_points", columnDefinition = "text")
	private String keyPoints;
	@Column(name = "difficult_points", columnDefinition = "text")
	private String difficultPoints;
	@Column(name = "teaching_method", columnDefinition = "text")
	private String teachingMethod;
	@Column(name = "classroom_activity", columnDefinition = "text")
	private String classroomActivity;
	@Column(name = "assessment_design", columnDefinition = "text")
	private String assessmentDesign;
	@Column(name = "after_class_reflection", columnDefinition = "text")
	private String afterClassReflection;
	@Column(name = "safety_notes", columnDefinition = "text")
	private String safetyNotes;
	@Column(name = "equipment_requirements", columnDefinition = "text")
	private String equipmentRequirements;
	@Column(name = "published_version_no")
	private Integer publishedVersionNo;
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
	@Column(nullable = false)
	private boolean archived;
}
