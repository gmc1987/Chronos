package com.chronos.education.scheduling.model;
import jakarta.persistence.*; import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor @Table(name="edu_question_option")
public class QuestionOption { @Id @Column(length=64) private String id; @Column(name="question_id",nullable=false,length=64) private String questionId; @Column(name="option_key",nullable=false,length=8) private String optionKey; @Column(name="option_text",nullable=false,columnDefinition="text") private String optionText; @Column(name="sort_order") private Integer sortOrder=0; }
