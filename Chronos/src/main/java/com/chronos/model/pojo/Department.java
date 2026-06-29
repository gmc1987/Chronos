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
@Table(name = "t_department", comment = "部门表")
public class Department extends BaseEntity {

	@Column(name = "department_name", nullable = false, length = 100)
	private String departmentName;
	
	@Column(name = "department_code", nullable = false, length = 100)
	private String departmentCode;
	
	@Column(name = "org_id", nullable = false, length = 64)
	private String orgId;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_department_id", nullable = true, referencedColumnName = "id")
	private Department parentDepartment;
	
	@Column(name = "level", nullable = false)
	private Integer level;
	
	@JsonIgnore
	@OneToMany(mappedBy = "parentDepartment", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private Set<Department> childDepartments;
	
	@Column(name = "description", length = 500)
	private String description;
	
}
