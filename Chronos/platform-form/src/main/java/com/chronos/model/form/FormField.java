package com.chronos.model.form;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "form_field", uniqueConstraints = @UniqueConstraint(columnNames = { "form_id", "field_key" }))
@Getter
@Setter
public class FormField extends BaseEntity {
	@Column(name = "form_id", nullable = false, length = 64)
	private String formId;
	@Column(name = "field_key", nullable = false, length = 100)
	private String fieldKey;
	@Column(name = "field_label", nullable = false, length = 160)
	private String fieldLabel;
	@Column(name = "field_type", nullable = false, length = 40)
	private String fieldType = "TEXT";
	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder = 0;
	@Column(nullable = false)
	private Boolean required = false;
	@Lob
	@Column(name = "options_json")
	private String optionsJson;
}
