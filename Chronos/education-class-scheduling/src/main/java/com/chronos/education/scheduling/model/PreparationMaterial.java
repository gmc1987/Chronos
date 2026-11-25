package com.chronos.education.scheduling.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_preparation_material")
public class PreparationMaterial {
	@Id
	@Column(length = 64)
	private String id;

	@Column(name = "preparation_id", nullable = false, length = 64)
	private String preparationId;

	@Column(name = "file_id", length = 64)
	private String fileId;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(name = "metadata_json", columnDefinition = "text")
	private String metadataJson;

	@Column(name = "bind_state", nullable = false)
	private String bindState = "PENDING_BIND";

	// These columns were created as quoted identifiers by the existing audit migration.
	@Column(name = "\"boundAt\"")
	private Instant boundAt;

	@Column(name = "\"createBy\"")
	private String createBy;

	@Column(name = "\"createTime\"")
	private Instant createTime;
}
