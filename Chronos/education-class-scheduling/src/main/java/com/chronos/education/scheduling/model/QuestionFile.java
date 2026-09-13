package com.chronos.education.scheduling.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter @NoArgsConstructor
@Table(name = "edu_question_file")
@IdClass(QuestionFileId.class)
public class QuestionFile {
	@Id @Column(name = "question_id", length = 64) private String questionId;
	@Id @Column(name = "file_id", length = 64) private String fileId;
}
