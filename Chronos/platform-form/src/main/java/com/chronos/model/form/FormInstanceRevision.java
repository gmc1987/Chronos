package com.chronos.model.form;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/** Immutable copy of each changed draft/submission of a runtime form. */
@Entity
@Immutable
@Getter
@Setter
@Table(
		name = "form_instance_revision",
		indexes = @Index(name = "idx_form_revision_workflow", columnList = "workflow_instance_id"),
		uniqueConstraints = @UniqueConstraint(
				name = "uk_form_revision_sequence",
				columnNames = { "form_instance_id", "revision_no" }))
public class FormInstanceRevision extends BaseEntity {
	@Column(name = "form_instance_id", nullable = false, length = 64)
	private String formInstanceId;

	@Column(name = "workflow_instance_id", nullable = false, length = 64)
	private String workflowInstanceId;

	@Column(name = "form_id", nullable = false, length = 64)
	private String formId;

	@Column(name = "node_key", nullable = false, length = 100)
	private String nodeKey;

	@Column(name = "revision_no", nullable = false)
	private Integer revisionNo;

	@Column(nullable = false, length = 128)
	private String owner;

	@Column(nullable = false, length = 30)
	private String status;

	@Column(name = "data_json", nullable = false, columnDefinition = "text")
	private String dataJson;
}
