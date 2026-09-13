package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 知识点树支持全局学科/课程关联，保存时由服务校验 parentId 不形成环。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_knowledge_point")
public class KnowledgePoint extends BaseEntity {
	@Column(name = "parent_id", length = 64)
	private String parentId;
	@Column(name = "school_id", nullable = false, length = 64)
	private String schoolId = "LEGACY";
	@Column(name = "subject_id", length = 64)
	private String subjectId;
	@Column(name = "course_id", length = 64)
	private String courseId;
	@Column(nullable = false, length = 200)
	private String name;
	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder = 0;
	@Column(nullable = false)
	private boolean enabled = true;
	@Column(nullable = false)
	private boolean archived;
	@Column(length = 64)
	private String code;
	@Column(columnDefinition = "text")
	private String description;
	@Column(name = "learning_objective", columnDefinition = "text")
	private String learningObjective;
	private Short level;
	@Column(nullable = false, length = 24)
	private String status = "ACTIVE";
	@jakarta.persistence.Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
