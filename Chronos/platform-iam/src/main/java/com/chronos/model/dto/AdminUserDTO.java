package com.chronos.model.dto;

import java.io.Serializable;
import java.util.Set;

import com.chronos.model.base.BaseDTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@SuppressWarnings("serial")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AdminUserDTO extends BaseDTO implements Serializable {
	private String id;
	@NotBlank(message = "username required")
	@Size(max = 100)
	private String username;
	@Size(min = 10, max = 200)
	private String password;
	@Email
	private String email;
	private String displayName;
	private String phone;
	private String avatarUrl;
	private String organizationId;
	private String positionName;
	private String employeeId;
	private String accountType;
	private Boolean accountLocked;
	private Boolean mustChangePassword;

	private Integer status;
	private Set<String> roleIds;
	private String createBy;
	private String lastUpdateBy;

}
