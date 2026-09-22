package com.chronos.education.grade.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_grade_rule")
public class GradeRule extends BaseEntity {
    @Column(name = "rule_set_id", nullable = false, length = 64)
    private String ruleSetId;
    @Column(name = "min_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal minScore;
    @Column(name = "max_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxScore;
    @Column(name = "grade_level", nullable = false, length = 16)
    private String gradeLevel;
    @Column(name = "grade_point", nullable = false, precision = 5, scale = 2)
    private BigDecimal gradePoint;
    @Column(nullable = false)
    private Boolean passed;
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
