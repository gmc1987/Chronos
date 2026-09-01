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
@Table(name = "t_menu", comment = "菜单表")
public class Menu extends BaseEntity {
	
	private static final long serialVersionUID = 1L;
	
	@Column(name = "menu_name", length = 200, nullable = false)
	private String menuName;
	
	@Column(name = "path", length = 500, nullable = true)
	private String path;

	@Column(name = "parent_id", length = 64, nullable = true)
	private String parentId;
	
	@Column(name = "order_num", nullable = true)
	private Integer orderNum;

}
