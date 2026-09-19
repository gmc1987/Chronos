package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity @Getter @Setter @Table(name="edu_data_daily_snapshot",
 uniqueConstraints=@UniqueConstraint(columnNames={"snapshot_date","campus_id","metric_code"}))
public class DataDailySnapshot extends BaseEntity {
 @Column(name="snapshot_date",nullable=false) private LocalDate snapshotDate;
 @Column(name="campus_id",length=64) private String campusId;
 @Column(name="metric_code",nullable=false,length=80) private String metricCode;
 @Column(name="metric_value",nullable=false,precision=18,scale=4) private BigDecimal metricValue;
 @Column(name="dimension_json",columnDefinition="text") private String dimensionJson;
}
