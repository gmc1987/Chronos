package com.chronos.model.pojo;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DynamicUpdate
@DynamicInsert
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "t_dict", comment = "字典表")
public class DictItem extends BaseEntity {
	
	private static final long serialVersionUID = 1L;
	
	@Column(name = "dict_code", length = 100, nullable = false)
	private String dictCode;

	@Column(name = "dict_name", length = 200, nullable = false)
	private String dictName;
	
	@Column(name = "parent_id", length = 64, nullable = true)
	private String parentId;
	
	@Column(name = "dict_value", length = 200, nullable = true)
	private String dictValue;

	@Column(name = "status", nullable = false)
	private Integer status = Integer.valueOf(1);
}
