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
	@Column(name="campus_id", length=64) private String campusId;
	@Column(name="owner_teacher_id", length=64) private String ownerTeacherId;
	@Column(nullable = false, length = 200)
	private String title;
	@Column(nullable = false, length = 24)
	private String status = "DRAFT";
	@Column(name = "share_scope", nullable = false, length = 32)
	private String shareScope = "PRIVATE";
	@jakarta.persistence.Version @Column(name="row_version", nullable=false) private Long rowVersion=0L;
	@Column(nullable = false)
	private boolean archived;
}
