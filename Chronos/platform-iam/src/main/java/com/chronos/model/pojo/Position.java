package com.chronos.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Getter @Setter @NoArgsConstructor
@Table(name = "t_position", comment = "岗位")
public class Position extends BaseEntity {
    @Column(name = "position_code", length = 100, nullable = false, unique = true) private String positionCode;
    @Column(name = "position_name", length = 100, nullable = false) private String positionName;
    @Column(name = "position_category", length = 50) private String positionCategory;
    @Column(name = "position_level", length = 50) private String positionLevel;
    @Column(name = "management", nullable = false) private Boolean management = false;
    @Column(name = "status", nullable = false) private Integer status = 1;
    @Column(name = "sort_order", nullable = false) private Integer sortOrder = 0;
    @Column(name = "description", length = 500) private String description;
}
