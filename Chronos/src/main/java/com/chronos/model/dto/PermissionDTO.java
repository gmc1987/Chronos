package com.chronos.model.dto;

import java.io.Serializable;

import com.chronos.model.base.BaseDTO;

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
public class PermissionDTO extends BaseDTO implements Serializable {
	private String id;
	@NotBlank
	@Size(max = 200)
	private String permissionName;

	@NotBlank
	@Size(max = 200)
	private String permissionCode;
	@Size(max = 500)
	private String description;

}
