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
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table(name = "t_admin_user", comment = "后台用户表")
public class AdminUser extends BaseEntity {
	private static final long serialVersionUID = 1L;
	@Column(name = "username", length = 100, nullable = false)
	private String username;

	@Column(name = "password", length = 200, nullable = false)
	private String password;

	@Column(name = "email", length = 200, nullable = true)
	private String email;

	@Column(name = "status", nullable = false)
	private Integer status;

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "t_user_role", joinColumns = { @JoinColumn(name = "user_id") }, inverseJoinColumns = {
			@JoinColumn(name = "role_id") })
	private Set<Role> roles = new HashSet<>();

}