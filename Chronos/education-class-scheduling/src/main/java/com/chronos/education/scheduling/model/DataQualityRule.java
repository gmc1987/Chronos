package com.chronos.education.scheduling.model;
import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter;
@Entity @Getter @Setter @Table(name="data_quality_rule")
public class DataQualityRule extends BaseEntity {
 @Column(name="rule_code",nullable=false,unique=true,length=64) private String ruleCode;
 @Column(name="rule_name",nullable=false,length=200) private String ruleName;
 @Column(name="metric_code",length=80) private String metricCode;
 @Column(nullable=false,columnDefinition="text") private String expression;
 @Column(nullable=false,length=16) private String severity="MEDIUM";
 @Column(nullable=false) private boolean enabled=true;
}
