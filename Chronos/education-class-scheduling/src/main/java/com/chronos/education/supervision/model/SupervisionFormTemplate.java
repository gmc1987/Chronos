package com.chronos.education.supervision.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "edu_supervision_form_template")
@Getter
@Setter
@NoArgsConstructor
public class SupervisionFormTemplate extends BaseEntity {
	@Column(name = "school_id", nullable = false, length = 64) private String schoolId;
	@Column(nullable = false, length = 200) private String name;
	@Column(name = "form_definition_id", nullable = false, length = 64) private String formDefinitionId;
	@Column(name = "version_no", nullable = false) private Integer versionNo;
	@Column(nullable = false, length = 24) private String status = "DRAFT";
}
