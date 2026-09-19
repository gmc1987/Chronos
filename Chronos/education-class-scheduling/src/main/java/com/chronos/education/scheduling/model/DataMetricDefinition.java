package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Getter @Setter @Table(name = "edu_data_metric_definition")
public class DataMetricDefinition extends BaseEntity {
 @Column(name="metric_code",nullable=false,unique=true,length=80) private String metricCode;
 @Column(name="metric_name",nullable=false,length=200) private String metricName;
 @Column(nullable=false,length=32) private String category;
 @Column(length=24) private String unit;
 @Column(nullable=false,columnDefinition="text") private String definition;
 @Column(nullable=false) private boolean enabled = true;
}
