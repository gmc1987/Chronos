package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
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
	@Column(name = "offering_id", length = 64)
	private String offeringId;
	@Column(nullable = false, length = 200)
	private String name;
	@Column(nullable = false, length = 24)
	private String status = "DRAFT";
	@Column(length = 128)
	private String subject;
	@Column(length = 64)
	private String grade;
	@Column(nullable = false)
	private boolean archived;
}
