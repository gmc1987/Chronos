package com.chronos.education.scheduling.model;
import jakarta.persistence.*; import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor @Table(name="edu_research_material")
public class ResearchMaterial { @Id @Column(length=64) private String id; @Column(name="activity_id",nullable=false,length=64) private String activityId; @Column(name="file_id",length=64) private String fileId; @Column(nullable=false,length=200) private String title; }
