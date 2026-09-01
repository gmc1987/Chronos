package com.chronos.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Getter @Setter @NoArgsConstructor
@Table(name = "t_job_title", comment = "职务职称")
public class JobTitle extends BaseEntity {
    @Column(name = "title_code", length = 100, nullable = false, unique = true) private String titleCode;
    @Column(name = "title_name", length = 100, nullable = false) private String titleName;
    @Column(name = "title_type", length = 32, nullable = false) private String titleType = "DUTY";
    @Column(name = "title_level", length = 50) private String titleLevel;
    @Column(name = "status", nullable = false) private Integer status = 1;
    @Column(name = "sort_order", nullable = false) private Integer sortOrder = 0;
}
