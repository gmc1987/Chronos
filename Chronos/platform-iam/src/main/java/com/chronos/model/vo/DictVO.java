package com.chronos.model.vo;

import java.io.Serializable;
import java.time.LocalDateTime;
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
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class DictVO extends BaseVO implements Serializable {
	private String id;
	private String dictCode;
	private String dictName;
	private String parentId;
	private String dictValue;
	private Integer status;
	private LocalDateTime createTime;
	private LocalDateTime lastUpdateTime;
	private List<com.chronos.model.vo.DictVO> children;
}
