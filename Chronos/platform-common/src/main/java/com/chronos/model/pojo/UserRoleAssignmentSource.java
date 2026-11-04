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
@Table(name = "t_user_role_assignment_source", uniqueConstraints = @UniqueConstraint(
		name = "uk_user_role_assignment_source",
		columnNames = {"user_id", "role_id", "source_type", "source_key"}))
public class UserRoleAssignmentSource extends BaseEntity {
	@Column(name = "user_id", length = 64, nullable = false)
	private String userId;

	@Column(name = "role_id", length = 64, nullable = false)
	private String roleId;

	@Column(name = "source_type", length = 32, nullable = false)
	private String sourceType;

	@Column(name = "source_key", length = 128, nullable = false)
	private String sourceKey;
}
