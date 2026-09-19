package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity @Getter @Setter @Table(name="edu_data_report_task",
 uniqueConstraints=@UniqueConstraint(columnNames={"report_type","requested_date","campus_id"}))
public class DataReportTask extends BaseEntity {
 @Column(name="report_type",nullable=false,length=32) private String reportType;
 @Column(name="requested_date",nullable=false) private LocalDate requestedDate;
 @Column(name="campus_id",length=64) private String campusId;
 @Column(nullable=false,length=24) private String status="PENDING";
 @Column(name="file_id",length=64) private String fileId;
 @Column(name="error_message",columnDefinition="text") private String errorMessage;
}
