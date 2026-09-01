package com.chronos.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Getter @Setter @NoArgsConstructor
@Table(name = "t_role_data_scope", comment = "角色数据权限")
public class RoleDataScope extends BaseEntity {
    @Column(name = "role_id", length = 64, nullable = false) private String roleId;
    @Column(name = "scope_type", length = 32, nullable = false) private String scopeType;
    @Column(name = "organization_id", length = 64) private String organizationId;
    @Column(name = "organization_unit_id", length = 64) private String organizationUnitId;
    @Column(name = "employee_id", length = 64) private String employeeId;
}
