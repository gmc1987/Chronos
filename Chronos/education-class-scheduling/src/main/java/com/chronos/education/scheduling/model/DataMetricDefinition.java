package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Getter @Setter @Table(name = "data_metric_definition")
public class DataMetricDefinition extends BaseEntity {
 @Column(name="metric_code",nullable=false,unique=true,length=80) private String metricCode;
 @Column(name="metric_name",nullable=false,length=200) private String metricName;
 @Column(nullable=false,length=32) private String category;
 @Column(length=24) private String unit;
 @Column(nullable=false,columnDefinition="text") private String definition;
 @Column(name="refresh_policy",nullable=false,length=32) private String refreshPolicy="DAILY";
 @Column(name="owner",length=128) private String owner;
 @Column(name="dimension_schema",columnDefinition="text") private String dimensionSchema;
 @Column(name="source_version",length=64) private String sourceVersion;
 @Column(nullable=false) private boolean enabled = true;
}
