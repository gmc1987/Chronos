package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 教研组数据范围根；活动、成员、资料、成果均通过 groupId 管理。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_research_group")
public class ResearchGroup extends BaseEntity {
	@Column(nullable = false, length = 200)
	private String name;
	@Column(name = "subject_id", length = 64)
	private String subjectId;
	@Column(nullable = false, length = 24)
	private String status = "ACTIVE";
	@Column(nullable = false)
	private boolean archived;
}
