package com.chronos.model.form;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "form_definition", uniqueConstraints = @UniqueConstraint(columnNames = { "form_key", "version" }))
@Getter
@Setter
public class FormDefinition extends BaseEntity {
	@Column(name = "form_key", nullable = false, length = 100)
	private String formKey;
	@Column(name = "form_name", nullable = false, length = 160)
	private String formName;
	@Column(nullable = false, length = 40)
	private String version = "v1";
	@Column(nullable = false, length = 30)
	private String status = "DRAFT";
	@Column(length = 500)
	private String description;
	@Column(name = "published_at")
	private LocalDateTime publishedAt;
}
