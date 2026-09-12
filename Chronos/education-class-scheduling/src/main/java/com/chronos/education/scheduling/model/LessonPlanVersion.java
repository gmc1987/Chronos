package com.chronos.education.scheduling.model;
import jakarta.persistence.*; import lombok.*; import java.time.*;
@Entity @Getter @Setter @NoArgsConstructor @Table(name="edu_lesson_plan_version")
public class LessonPlanVersion { @Id @Column(length=64) private String id; @Column(name="lesson_plan_id",nullable=false,length=64) private String lessonPlanId; @Column(nullable=false) private Integer versionNo; @Column(columnDefinition="text") private String content; @Column(name="file_id",length=64) private String fileId; @Column(nullable=false,length=24) private String status="DRAFT"; @Column(name="create_by",nullable=false) private String createBy; @Column(name="create_time",nullable=false) private Instant createTime; }
