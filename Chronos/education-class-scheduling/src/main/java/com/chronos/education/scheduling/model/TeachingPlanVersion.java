package com.chronos.education.scheduling.model;
import jakarta.persistence.*; import lombok.*; import java.time.*;
@Entity @Getter @Setter @NoArgsConstructor @Table(name="edu_teaching_plan_version")
public class TeachingPlanVersion { @Id @Column(length=64) private String id; @Column(name="plan_id",nullable=false,length=64) private String planId; @Column(nullable=false) private Integer versionNo; @Column(nullable=false,length=24) private String status="DRAFT"; @Column(columnDefinition="text") private String snapshotJson; private Instant publishedAt; private Instant archivedAt; @Column(name="create_by",nullable=false) private String createBy; @Column(name="create_time",nullable=false) private Instant createTime; }
