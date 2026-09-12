package com.chronos.education.scheduling.model;
import jakarta.persistence.*; import lombok.*; import java.time.*;
@Entity @Getter @Setter @NoArgsConstructor @Table(name="edu_lesson_plan_review")
public class LessonPlanReview { @Id @Column(length=64) private String id; @Column(name="lesson_plan_version_id",nullable=false,length=64) private String lessonPlanVersionId; @Column(name="reviewer_id",nullable=false,length=64) private String reviewerId; @Column(nullable=false,length=24) private String decision; @Column(columnDefinition="text") private String comment; @Column(name="reviewed_at",nullable=false) private Instant reviewedAt; }
