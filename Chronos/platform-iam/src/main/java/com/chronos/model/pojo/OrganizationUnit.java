/**
 * 
 */
package com.chronos.model.pojo;

import java.util.Set;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
@Table(name = "t_organization_unit", comment = "组织单元表")
public class OrganizationUnit extends BaseEntity {

	@Column(name = "organization_unit_name", nullable = false, length = 100)
	private String organizationUnitName;
	
	@Column(name = "organization_unit_code", nullable = false, length = 100)
	private String organizationUnitCode;

	@Column(name = "unit_type", length = 32)
	private String unitType = "DEPARTMENT";
	
	@Column(name = "org_id", nullable = false, length = 64)
	private String orgId;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_organization_unit_id", nullable = true, referencedColumnName = "id")
	private OrganizationUnit parentOrganizationUnit;
	
	@Column(name = "level", nullable = false)
	private Integer level;

	@Column(name = "tree_path", length = 1000)
	private String treePath;

	@Column(name = "sort_order")
	private Integer sortOrder = 0;

	@Column(name = "leader_employee_id", length = 64)
	private String leaderEmployeeId;

	@Column(name = "status")
	private Integer status = 1;
	
	@JsonIgnore
	@OneToMany(mappedBy = "parentOrganizationUnit", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private Set<OrganizationUnit> childOrganizationUnit;
	
	@Column(name = "description", length = 500)
	private String description;
	
}
