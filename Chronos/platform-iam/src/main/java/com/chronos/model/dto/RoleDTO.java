package com.chronos.model.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import com.chronos.model.base.BaseDTO;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Generated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@SuppressWarnings("serial")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class RoleDTO extends BaseDTO implements Serializable {
	private String id;
	@Size(max = 100)
	private String roleName;
	@Size(max = 100)
	private String roleCode;
	private Integer status;
	private Boolean builtIn;
	@Size(max = 500)
	private String description;

	@Generated
	private Set<String> permissionIds;
	private Set<String> menuIds;
	private List<RoleMenuPermissionDTO> menuPermissions;
	private List<RoleDataScopeDTO> dataScopes;

}
