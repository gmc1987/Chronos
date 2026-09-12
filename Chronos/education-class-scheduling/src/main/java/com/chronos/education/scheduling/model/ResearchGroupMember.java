package com.chronos.education.scheduling.model;
import jakarta.persistence.*; import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor @Table(name="edu_research_group_member")
public class ResearchGroupMember { @Id @Column(length=64) private String id; @Column(name="group_id",nullable=false,length=64) private String groupId; @Column(name="teacher_id",nullable=false,length=64) private String teacherId; @Column(length=32) private String role; }
