package com.chronos.model.pojo;

import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
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
@Table(name = "t_role", comment = "角色表")
public class Role extends BaseEntity {
	
	private static final long serialVersionUID = 1L;
	
	@Column(name = "role_name", length = 100, nullable = false)
	private String roleName;
	
	@Column(name = "description", length = 500, nullable = true)
	private String description;

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "t_role_menu", joinColumns = { @JoinColumn(name = "role_id") }, inverseJoinColumns = {
			@JoinColumn(name = "menu_id") })
	private Set<Menu> menus = new HashSet<>();

}