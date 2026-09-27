package com.chronos.education.classgroup.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_class_group", uniqueConstraints = @UniqueConstraint(
		name = "uk_edu_class_group_class", columnNames = "class_id"))
public class ClassGroup extends BaseEntity {
	@Column(name = "school_id", length = 64, nullable = false)
	private String schoolId;
	@Column(name = "class_id", length = 64, nullable = false)
	private String classId;
	@Column(name = "name", length = 200, nullable = false)
	private String name;
	@Column(name = "status", length = 24, nullable = false)
	private String status = "ACTIVE";
	@Column(name = "last_sync_key", length = 128)
	private String lastSyncKey;
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
