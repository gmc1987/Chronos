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

@SuppressWarnings("serial")
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@IdClass(RoleMenuPermissionId.class)
@Table(name = "t_role_menu_permission", comment = "角色-菜单-权限关联")
public class RoleMenuPermission implements Serializable {
	@Id
	@Column(name = "role_id", nullable = false, length = 64)
	private String roleId;
	@Id
	@Column(name = "menu_id", nullable = false, length = 64)
	private String menuId;

	@Id
	@Column(name = "permission_id", nullable = false, length = 64)
	private String permissionId;

}
