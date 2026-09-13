package com.chronos.education.scheduling.model;
import jakarta.persistence.*; import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor @Table(name="edu_teaching_plan_item")
public class TeachingPlanItem { @Id @Column(length=64) private String id; @Column(name="plan_id",nullable=false,length=64) private String planId; @Column(name="chapter_no") private Integer chapterNo; @Column(name="chapter_name",length=200) private String chapterName; @Column(name="lesson_hours") private Integer lessonHours; @Column(columnDefinition="text") private String objectives; @Column(name="key_points",columnDefinition="text") private String keyPoints; @Column(name="difficult_points",columnDefinition="text") private String difficultPoints; @Column(name="sort_order") private Integer sortOrder=0; }
