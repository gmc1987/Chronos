package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 教学计划主表；计划项和发布版本分别落在独立表中。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_teaching_plan")
public class TeachingPlan extends BaseEntity {
	@Column(name = "school_id", nullable = false, length = 64)
	private String schoolId = "LEGACY";
	@Column(name = "campus_id", length = 64)
	private String campusId;
	@Column(name = "owner_teacher_id", length = 64)
	private String ownerTeacherId;
	@Column(name = "offering_id", length = 64)
	private String offeringId;
	@Column(name = "semester_id", nullable = false, length = 64)
	private String semesterId;
	@Column(name = "course_id", nullable = false, length = 64)
	private String courseId;
	@Column(nullable = false, length = 200)
	private String name;
	@Column(nullable = false, length = 24)
	private String status = "DRAFT";
	@Column(length = 128)
	private String subject;
	@Column(length = 64)
	private String grade;
	@Column(name = "plan_type", nullable = false, length = 24)
	private String planType;
	@Column(name = "total_hours", nullable = false)
	private Integer totalHours;
	@Column(columnDefinition = "text")
	private String objective;
	@Column(name = "assessment_method", columnDefinition = "text")
	private String assessmentMethod;
	@Column(columnDefinition = "text")
	private String remarks;
	@Column(name = "current_version_no", nullable = false)
	private Integer currentVersionNo = 0;
	@Column(name = "published_version_no")
	private Integer publishedVersionNo;
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
	@Column(nullable = false)
	private boolean archived;
}
