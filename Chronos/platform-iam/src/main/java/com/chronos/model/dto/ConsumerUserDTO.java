package com.chronos.model.dto;

import java.io.Serializable;

import com.chronos.model.base.BaseDTO;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Generated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@SuppressWarnings("serial")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ConsumerUserDTO extends BaseDTO implements Serializable {

	private String id;

	@Size(min = 3, max = 100)
	private String username;

	@Size(min = 6, max = 100)
	private String password;

	@Generated
	private String email;

	@Pattern(regexp = "^$|^\\+?\\d{7,15}$", message = "invalid phone")
	private String phone;

	private Integer status;

	private String customerType;

}
