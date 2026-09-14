package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 合班教学任务与来源行政班的关系；学生仍以教学班成员表为排课依据。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "edu_combined_offering_source_class",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_combined_offering_source_class",
				columnNames = { "offering_id", "administrative_class_id" }))
public class CombinedOfferingSourceClass extends BaseEntity {
	@Column(name = "offering_id", length = 64, nullable = false)
	private String offeringId;

	@Column(name = "administrative_class_id", length = 64, nullable = false)
	private String administrativeClassId;
}
