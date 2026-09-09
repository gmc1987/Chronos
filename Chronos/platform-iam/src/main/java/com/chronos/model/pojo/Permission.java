package com.chronos.model.pojo;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DynamicUpdate
@DynamicInsert
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "t_permission", comment = "权限表")
public class Permission extends BaseEntity {
	
	private static final long serialVersionUID = 1L;
	
	@Column(name = "permission_name", length = 200, nullable = false)
	private String permissionName;

	@Column(name = "permission_code", length = 200, nullable = false, unique = true)
	private String permissionCode;

	@Column(name = "permission_type", length = 32)
	private String permissionType = "API";

	@Column(name = "menu_id", length = 64)
	private String menuId;

	@Column(name = "action_type", length = 32)
	private String actionType;

	@Column(name = "resource_type", length = 64)
	private String resourceType;

	@Column(name = "scope_type", length = 32)
	private String scopeType;

	@Column(name = "config_json", columnDefinition = "text")
	private String configJson;

	@Column(name = "resource_pattern", length = 500)
	private String resourcePattern;

	@Column(name = "http_method", length = 16)
	private String httpMethod;

	@Column(name = "status")
	private Integer status = 1;

	@Column(name = "built_in", nullable = false, columnDefinition = "boolean default false")
	private Boolean builtIn = false;
	
	@Column(name = "description", length = 500, nullable = true)
	private String description;

}
