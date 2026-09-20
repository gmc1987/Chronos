package com.chronos.education.grade.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_grade_rule_set")
public class GradeRuleSet extends BaseEntity {
    @Column(nullable = false, length = 64)
    private String schoolId;
    @Column(nullable = false, length = 128)
    private String name;
    @Column(nullable = false, length = 32)
    private String code;
    @Column(nullable = false, length = 24)
    private String status = "DRAFT";
    @Column(nullable = false)
    private Integer versionNo = 1;
    @Column(nullable = false)
    private Boolean platformDefault = false;
    @Column(nullable = false, length = 24)
    private String makeupStrategy = "OVERWRITE";
    @Version
    @Column(nullable = false)
    private Long rowVersion = 0L;
}
