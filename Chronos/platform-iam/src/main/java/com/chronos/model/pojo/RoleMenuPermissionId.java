package com.chronos.model.pojo;

import java.io.Serializable;
import java.util.Objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@SuppressWarnings("serial")
@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter
public class RoleMenuPermissionId implements Serializable {
	private String roleId;

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof RoleMenuPermissionId))
			return false;
		RoleMenuPermissionId other = (RoleMenuPermissionId) o;
		if (!other.canEqual(this))
			return false;
		Object this$roleId = this.roleId, other$roleId = other.roleId;
		if ((this$roleId == null) ? (other$roleId != null) : !this$roleId.equals(other$roleId))
			return false;
		Object this$menuId = this.menuId, other$menuId = other.menuId;
		if ((this$menuId == null) ? (other$menuId != null) : !this$menuId.equals(other$menuId))
			return false;
		Object this$permissionId = this.permissionId, other$permissionId = other.permissionId;
		return !((this$permissionId == null) ? (other$permissionId != null)
				: !this$permissionId.equals(other$permissionId));
	}

	private String menuId;
	private String permissionId;

	protected boolean canEqual(Object other) {
		return other instanceof RoleMenuPermissionId;
	}

	@Override
    public int hashCode() {
        return Objects.hash(roleId, menuId, permissionId);
    }

}
