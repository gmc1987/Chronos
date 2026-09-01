package com.chronos.model.pojo;

import java.util.HashSet;
import java.util.Set;
import java.time.LocalDateTime;

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
import com.fasterxml.jackson.annotation.JsonIgnore;

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
	@Column(name = "username", length = 100, nullable = false, unique = true)
	private String username;

	@Column(name = "password", length = 200, nullable = false)
	@JsonIgnore
	private String password;

	@Column(name = "email", length = 200, nullable = true)
	private String email;

	@Column(name = "display_name", length = 100)
	private String displayName;

	@Column(name = "phone", length = 32)
	private String phone;

	@Column(name = "avatar_url", length = 500)
	private String avatarUrl;

	@Column(name = "organization_id", length = 64)
	private String organizationId;

	@Column(name = "position_name", length = 100)
	private String positionName;

	@Column(name = "employee_id", length = 64)
	private String employeeId;

	@Column(name = "account_type", length = 32)
	private String accountType = "STAFF";

	@Column(name = "account_locked")
	private Boolean accountLocked = false;

	@Column(name = "failed_login_attempts")
	private Integer failedLoginAttempts = 0;

	@Column(name = "lock_until")
	private LocalDateTime lockUntil;

	@Column(name = "password_changed_at")
	private LocalDateTime passwordChangedAt;

	@Column(name = "must_change_password")
	private Boolean mustChangePassword = false;

	@Column(name = "last_login_at")
	private LocalDateTime lastLoginAt;

	@Column(name = "last_login_ip", length = 64)
	private String lastLoginIp;

	@Column(name = "token_version")
	private Integer tokenVersion = 0;

	@Column(name = "status", nullable = false)
	private Integer status;

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "t_user_role", joinColumns = { @JoinColumn(name = "user_id") }, inverseJoinColumns = {
			@JoinColumn(name = "role_id") })
	private Set<Role> roles = new HashSet<>();

}
