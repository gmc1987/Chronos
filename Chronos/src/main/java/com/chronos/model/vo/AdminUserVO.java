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
	private Integer status;
	private Set<RoleVO> roles;
}

