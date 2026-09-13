package com.chronos.education.scheduling.model;
import jakarta.persistence.*; import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor @Table(name="edu_research_activity_member")
public class ResearchActivityMember { @Id @Column(length=64) private String id; @Column(name="activity_id",nullable=false,length=64) private String activityId; @Column(name="teacher_id",nullable=false,length=64) private String teacherId; @Column(length=32) private String role; @Column(name="attendance_status",nullable=false,length=24) private String attendanceStatus="INVITED"; @Column(name="attendance_at") private java.time.LocalDateTime attendanceAt; @Column(name="leave_reason",columnDefinition="text") private String leaveReason; @Column(name="responded_at") private java.time.LocalDateTime respondedAt; }
