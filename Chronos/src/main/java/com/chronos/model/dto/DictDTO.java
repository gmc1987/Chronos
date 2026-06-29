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
public class DictDTO extends BaseDTO implements Serializable {
	private String id;
	@NotBlank
	@Size(max = 100)
	private String dictCode;
	@NotBlank
	@Size(max = 200)
	private String dictName;

	private String parentId;

	@Size(max = 200)
	private String dictValue;
	private Integer status;

}
