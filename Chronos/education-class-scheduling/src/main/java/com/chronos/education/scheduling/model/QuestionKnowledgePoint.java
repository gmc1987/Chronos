package com.chronos.education.scheduling.model;
import jakarta.persistence.*; import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor @Table(name="edu_question_knowledge_point")
@IdClass(QuestionKnowledgePointId.class) public class QuestionKnowledgePoint { @Id @Column(name="question_id",length=64) private String questionId; @Id @Column(name="knowledge_point_id",length=64) private String knowledgePointId; }
