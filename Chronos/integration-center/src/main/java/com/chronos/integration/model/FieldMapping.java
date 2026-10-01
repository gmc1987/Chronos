package com.chronos.integration.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "int_field_mapping")
@Getter
@Setter
public class FieldMapping extends BaseEntity {
	@Column(name = "connector_id", nullable = false, length = 64)
	private String connectorId;
	@Column(name = "object_type", nullable = false, length = 64)
	private String objectType;
	@Column(name = "source_path", nullable = false, length = 256)
	private String sourcePath;
	@Column(name = "target_field", nullable = false, length = 128)
	private String targetField;
	@Column(name = "transform_code", length = 64)
	private String transformCode;
	@Column(nullable = false)
	private Boolean required = false;
	@Column(name = "version_no", nullable = false)
	private Integer versionNo = 1;
}
