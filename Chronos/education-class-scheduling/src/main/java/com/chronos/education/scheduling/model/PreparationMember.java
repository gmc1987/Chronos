package com.chronos.education.scheduling.model;
import jakarta.persistence.*; import lombok.*; import java.time.*;
@Entity @Getter @Setter @NoArgsConstructor @Table(name="edu_preparation_member")
public class PreparationMember { @Id @Column(length=64) private String id; @Column(name="preparation_id",nullable=false,length=64) private String preparationId; @Column(name="teacher_id",nullable=false,length=64) private String teacherId; @Column(length=32) private String role; private Instant joinedAt; }
