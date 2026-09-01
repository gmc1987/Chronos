package com.chronos.model.pojo;

import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class RolePermissionId implements Serializable {
    private String roleId; private String permissionId;
    public RolePermissionId(String roleId, String permissionId) { this.roleId=roleId; this.permissionId=permissionId; }
    @Override public boolean equals(Object o) { return o instanceof RolePermissionId x && Objects.equals(roleId,x.roleId) && Objects.equals(permissionId,x.permissionId); }
    @Override public int hashCode() { return Objects.hash(roleId, permissionId); }
}
