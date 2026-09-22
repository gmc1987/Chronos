package com.chronos.education.grade.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity @Getter @Setter @NoArgsConstructor @Table(name="edu_assessment_scheme")
public class AssessmentScheme extends BaseEntity {
 @Column(name="school_id",nullable=false,length=64) private String schoolId;
 @Column(name="offering_id",nullable=false,length=64) private String offeringId;
 @Column(nullable=false,length=200) private String name;
 @Column(name="total_score",nullable=false,precision=8,scale=2) private BigDecimal totalScore;
 @Column(name="pass_score",nullable=false,precision=8,scale=2) private BigDecimal passScore;
 @Column(nullable=false,length=24) private String status="DRAFT";
 @Column(name="published_version_no") private Integer publishedVersionNo;
 @Column(name="grade_rule_set_id",length=64) private String gradeRuleSetId;
 @Version @Column(name="row_version",nullable=false) private Long rowVersion=0L;
}
