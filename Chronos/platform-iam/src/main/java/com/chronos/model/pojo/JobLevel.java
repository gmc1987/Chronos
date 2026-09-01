package com.chronos.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Getter @Setter @NoArgsConstructor
@Table(name = "t_job_level", comment = "员工职级")
public class JobLevel extends BaseEntity {
    @Column(name = "level_code", length = 100, nullable = false, unique = true) private String levelCode;
    @Column(name = "level_name", length = 100, nullable = false) private String levelName;
    @Column(name = "level_sequence", nullable = false) private Integer levelSequence = 0;
    @Column(name = "level_category", length = 50) private String levelCategory;
    @Column(name = "status", nullable = false) private Integer status = 1;
    @Column(name = "sort_order", nullable = false) private Integer sortOrder = 0;
    @Column(name = "description", length = 500) private String description;
}
