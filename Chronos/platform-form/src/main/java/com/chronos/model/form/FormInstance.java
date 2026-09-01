package com.chronos.model.form;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "form_instance", uniqueConstraints = @UniqueConstraint(columnNames = { "workflow_instance_id", "form_id",
		"node_key" }))
@Getter
@Setter
public class FormInstance extends BaseEntity {
	@Column(name = "workflow_instance_id", nullable = false, length = 64)
	private String workflowInstanceId;
	@Column(name = "form_id", nullable = false, length = 64)
	private String formId;
	@Column(name = "node_key", nullable = false, length = 100)
	private String nodeKey;
	@Column(name = "form_role", nullable = false, length = 30)
	private String formRole;
	@Column(nullable = false, length = 128)
	private String owner;
	@Column(nullable = false, length = 30)
	private String status = "DRAFT";
	@Lob
	@Column(name = "data_json", nullable = false)
	private String dataJson = "{}";
}
