package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Getter @Setter @Table(name="data_quality_issue")
public class DataQualityIssue extends BaseEntity {
 @Column(name="campus_id",length=64) private String campusId;
 @Column(name="metric_code",length=80) private String metricCode;
 @Column(name="rule_id",length=64) private String ruleId;
 @Column(nullable=false,length=200) private String title;
 @Column(columnDefinition="text") private String description;
 @Column(nullable=false,length=16) private String severity="MEDIUM";
 @Column(nullable=false,length=24) private String status="OPEN";
 @Column(name="owner_id",length=64) private String ownerId;
 @Column(name="due_date") private LocalDate dueDate;
 @Column(columnDefinition="text") private String resolution;
 @Column(name="resolved_at") private LocalDateTime resolvedAt;
}
