package com.chronos.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "t_person_external_identity", uniqueConstraints = @UniqueConstraint(
		name = "uk_person_external_identity",
		columnNames = {"organization_id", "person_type", "source_system", "external_person_key"}))
public class PersonExternalIdentity extends BaseEntity {
	@Column(name = "organization_id", length = 64, nullable = false)
	private String organizationId;

	@Column(name = "person_type", length = 32, nullable = false)
	private String personType;

	@Column(name = "person_id", length = 64, nullable = false)
	private String personId;

	@Column(name = "source_system", length = 64, nullable = false)
	private String sourceSystem;

	@Column(name = "external_person_key", length = 200, nullable = false)
	private String externalPersonKey;
}
