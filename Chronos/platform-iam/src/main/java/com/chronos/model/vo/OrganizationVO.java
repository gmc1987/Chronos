package com.chronos.model.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.chronos.model.base.BaseVO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class OrganizationVO extends BaseVO implements Serializable {
	private String id;
	private String organizationName;
	private String orgCode;
	private String organizationType;
	private String shortName;
	private String timezone;
	private Integer status;
	private Integer sortOrder;
	private String parentOrganizationId;
	private String description;
	private String mailingAddress;
	private String tel;
	private String organizationManager;
	private String organizationManagerName;
	private String industries;
	private LocalDateTime registerTime;
	private LocalDateTime lastUpdateTime;
}
