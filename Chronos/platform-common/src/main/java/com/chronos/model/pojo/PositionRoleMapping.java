package com.chronos.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "t_position_role_mapping")
public class PositionRoleMapping extends BaseEntity {
	@Column(name = "organization_id", length = 64)
	private String organizationId;

	@Column(name = "organization_unit_id", length = 64)
	private String organizationUnitId;

	@Column(name = "position_id", length = 64, nullable = false)
	private String positionId;

	@Column(name = "role_id", length = 64, nullable = false)
	private String roleId;

	@Column(name = "primary_assignment_only", nullable = false)
	private Boolean primaryAssignmentOnly = false;

	@Column(name = "department_leader_only", nullable = false)
	private Boolean departmentLeaderOnly = false;

	@Column(name = "priority", nullable = false)
	private Integer priority = 0;

	@Column(name = "status", nullable = false)
	private Integer status = 1;
}
