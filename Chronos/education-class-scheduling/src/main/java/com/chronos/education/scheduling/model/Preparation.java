package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 个人/集体备课聚合根，成员、资料、讨论和结论独立持久化。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_preparation")
public class Preparation extends BaseEntity {
	@Column(name = "offering_id", nullable = false, length = 64)
	private String offeringId;
	@Column(nullable = false, length = 200)
	private String title;
	@Column(name = "preparation_type", nullable = false, length = 16)
	private String preparationType;
	@Column(nullable = false, length = 24)
	private String status = "DRAFT";
	@Column(columnDefinition = "text")
	private String conclusion;
	@Column(nullable = false)
	private boolean archived;
}
