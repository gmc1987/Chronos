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
public class MenuDTO extends BaseDTO implements Serializable {
	private String id;
	@NotBlank
	@Size(max = 200)
	private String menuName;

	private String path;
	private String parentId;
	private Integer orderNum;
}
