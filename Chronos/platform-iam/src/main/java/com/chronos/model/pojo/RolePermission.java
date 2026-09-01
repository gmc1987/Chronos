package com.chronos.model.pojo;

import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor
@IdClass(RolePermissionId.class)
@Table(name = "t_role_permission", comment = "角色权限")
public class RolePermission implements Serializable {
    @Id @Column(name = "role_id", length = 64) private String roleId;
    @Id @Column(name = "permission_id", length = 64) private String permissionId;
}
