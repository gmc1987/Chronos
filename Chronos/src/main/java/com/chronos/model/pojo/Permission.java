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

	@Column(name = "permission_code", length = 200, nullable = false)
	private String permissionCode;
	
	@Column(name = "description", length = 500, nullable = true)
	private String description;

}
