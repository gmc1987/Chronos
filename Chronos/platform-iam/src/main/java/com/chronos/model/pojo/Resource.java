/**
 * 
 */
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

/**
 * 
 */
@SuppressWarnings("serial")
@Entity
@DynamicUpdate
@DynamicInsert
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "t_resource", comment = "员工表")
public class Resource extends BaseEntity {
	
	@Column(name = "resource_name", length = 100, nullable = false)
	private String resourceName;
	
	@Column(name = "resource_code", length = 100, nullable = false)
	private String resourceCode;
	
	@Column(name = "organization_unit_id", length = 64, nullable = false)
	private String organizationUnitId;
	
	@Column(name = "user_id", length = 64, nullable = false)
	private String userId;
	
	@Column(name = "position", length = 100, nullable = false)
	private String position;
	
	@Column(name = "position_level", length = 100, nullable = false)
	private String positionLevel;
	
	@Column(name = "is_manager", nullable = false)
	private Boolean isManager;
	
	@Column(name = "entry_date", length = 20, nullable = false)
	private String entryDate;
	
	@Column(name = "leave_date", length = 20, nullable = true)
	private String leaveDate;
	

}
