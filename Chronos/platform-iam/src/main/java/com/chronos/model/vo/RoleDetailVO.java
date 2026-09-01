package com.chronos.model.vo;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import com.chronos.model.base.BaseVO;
import com.chronos.model.dto.RoleMenuPermissionDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@SuppressWarnings("serial")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class RoleDetailVO extends BaseVO implements Serializable {
	private String id;
	private String roleName;
	private String roleCode;
	private Integer status;
	private Boolean builtIn;
	private String description;
	private Set<String> menuIds;
	private Set<String> permissionIds;
	private List<RoleMenuPermissionDTO> menuPermissions;
	private List<com.chronos.model.dto.RoleDataScopeDTO> dataScopes;

}
