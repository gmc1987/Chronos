package com.chronos.education.scheduling.model;
import jakarta.persistence.*; import lombok.*; import java.time.*;
@Entity @Getter @Setter @NoArgsConstructor @Table(name="edu_preparation_comment")
public class PreparationComment { @Id @Column(length=64) private String id; @Column(name="preparation_id",nullable=false,length=64) private String preparationId; @Column(name="author_id",nullable=false,length=64) private String authorId; @Column(nullable=false,columnDefinition="text") private String content; @Column(name="create_time",nullable=false) private Instant createTime; }
