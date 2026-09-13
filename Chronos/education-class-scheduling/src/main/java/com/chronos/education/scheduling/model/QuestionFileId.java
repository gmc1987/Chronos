package com.chronos.education.scheduling.model;

import java.io.Serializable;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class QuestionFileId implements Serializable {
	private String questionId;
	private String fileId;
}
