package com.chronos.education.scheduling.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Getter @Setter @NoArgsConstructor
@Table(name = "edu_question_reference")
public class QuestionReference {
	@Id @Column(length = 64) private String id;
	@Column(name = "question_id", nullable = false, length = 64) private String questionId;
	@Column(name = "version_id", nullable = false, length = 64) private String versionId;
	@Column(name = "consumer_type", nullable = false, length = 24) private String consumerType;
	@Column(name = "consumer_id", nullable = false, length = 64) private String consumerId;
	@Column(name = "create_time", nullable = false) private LocalDateTime createTime;
}
