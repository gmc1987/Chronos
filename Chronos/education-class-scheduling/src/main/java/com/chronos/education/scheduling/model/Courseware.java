package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 课件元数据；二进制始终由 platform-file 管理。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_courseware")
public class Courseware extends BaseEntity {
	@Column(name = "offering_id", nullable = false, length = 64)
	private String offeringId;
	@Column(nullable = false, length = 200)
	private String title;
	@Column(nullable = false, length = 24)
	private String status = "DRAFT";
	@Column(name = "share_scope", nullable = false, length = 32)
	private String shareScope = "PRIVATE";
	@Column(nullable = false)
	private boolean archived;
}
