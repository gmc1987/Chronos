package com.chronos.education.scheduling.model;
import jakarta.persistence.*; import lombok.*; import java.time.*;
@Entity @Getter @Setter @NoArgsConstructor @Table(name="edu_preparation_member")
public class PreparationMember { @Id @Column(length=64) private String id; @Column(name="preparation_id",nullable=false,length=64) private String preparationId; @Column(name="teacher_id",nullable=false,length=64) private String teacherId; @Column(length=32) private String role; @Column(name="joined_at") private Instant joinedAt; @Column(name="invitation_status",nullable=false) private String invitationStatus="PENDING"; private String invitedBy; private Instant invitedAt; private Instant respondedAt; private String responseComment; }
