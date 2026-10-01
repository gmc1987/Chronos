package com.chronos.education.scheduling.model;
import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter;
@Entity @Getter @Setter @Table(name="data_dashboard_widget")
public class DataDashboardWidget extends BaseEntity {
 @Column(name="dashboard_id",nullable=false,length=64) private String dashboardId;
 @Column(name="widget_code",nullable=false,length=64) private String widgetCode;
 @Column(name="metric_code",nullable=false,length=80) private String metricCode;
 @Column(nullable=false,length=200) private String title;
 @Column(name="position_no",nullable=false) private int positionNo;
 @Column(name="config_json",columnDefinition="text") private String configJson;
 @Column(nullable=false) private boolean enabled=true;
}
