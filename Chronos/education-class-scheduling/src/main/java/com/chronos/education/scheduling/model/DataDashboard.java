package com.chronos.education.scheduling.model;
import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter;
@Entity @Getter @Setter @Table(name="data_dashboard")
public class DataDashboard extends BaseEntity {
 @Column(name="dashboard_code",nullable=false,unique=true,length=64) private String dashboardCode;
 @Column(name="dashboard_name",nullable=false,length=200) private String dashboardName;
 @Column(nullable=false,length=32) private String category;
 @Column(nullable=false) private boolean enabled=true;
}
