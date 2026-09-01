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

	@Column(name = "resource_pattern", length = 500)
	private String resourcePattern;

	@Column(name = "http_method", length = 16)
	private String httpMethod;

	@Column(name = "status")
	private Integer status = 1;
	
	@Column(name = "description", length = 500, nullable = true)
	private String description;

}
