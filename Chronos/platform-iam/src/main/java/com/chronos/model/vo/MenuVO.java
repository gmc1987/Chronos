package com.chronos.model.vo;

import java.io.Serializable;
import java.util.List;

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
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class MenuVO extends BaseVO implements Serializable {
	private String id;
	private String menuName;
	private String path;
	private String parentId;
	private Integer orderNum;
	private List<com.chronos.model.vo.MenuVO> children;
}
