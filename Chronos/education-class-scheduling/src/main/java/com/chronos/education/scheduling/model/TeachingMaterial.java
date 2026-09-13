package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 教学材料元数据和版本文件引用分离。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_teaching_material")
public class TeachingMaterial extends BaseEntity {
	@Column(name = "offering_id", nullable = false, length = 64)
	private String offeringId;
	@Column(name="campus_id", length=64) private String campusId;
	@Column(name="owner_teacher_id", length=64) private String ownerTeacherId;
	@Column(nullable = false, length = 200)
	private String title;
	@Column(name = "material_type", length = 32)
	private String materialType;
	@Column(name = "share_scope", nullable = false, length = 32)
	private String shareScope = "PRIVATE";
	@jakarta.persistence.Version @Column(name="row_version", nullable=false) private Long rowVersion=0L;
	@Column(nullable = false)
	private boolean archived;
}
