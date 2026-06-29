package com.chronos.model.vo;

import java.io.Serializable;

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
@Getter
@Setter
public class PermissionVO extends BaseVO implements Serializable {
	private String id;
	private String permissionName;
	private String permissionCode;
	private String description;
}
