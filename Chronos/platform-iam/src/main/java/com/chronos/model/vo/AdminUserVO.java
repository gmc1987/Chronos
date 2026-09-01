package com.chronos.model.vo;

import java.io.Serializable;
import java.util.Set;

import com.chronos.model.base.BaseVO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@SuppressWarnings("serial")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class AdminUserVO extends BaseVO implements Serializable {
	private String id;
	private String username;
	private String email;
	private String displayName;
	private String phone;
	private String avatarUrl;
	private String organizationId;
	private String positionName;
	private String employeeId;
	private String accountType;
	private Boolean accountLocked;
	private Integer failedLoginAttempts;
	private java.time.LocalDateTime lockUntil;
	private Boolean mustChangePassword;
	private java.time.LocalDateTime lastLoginAt;
	private String lastLoginIp;
	private Integer status;
	private Set<RoleVO> roles;
}
