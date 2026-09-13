package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Getter @Setter @NoArgsConstructor
@Table(name = "edu_question_version")
public class QuestionVersion extends BaseEntity {
	@Column(name = "question_id", nullable = false, length = 64)
	private String questionId;
	@Column(name = "version_no", nullable = false)
	private Integer versionNo;
	@Column(name = "snapshot_json", nullable = false, columnDefinition = "jsonb")
	private String snapshotJson;
	@Column(name = "snapshot_hash", nullable = false, length = 64)
	private String snapshotHash;
	@Column(nullable = false, length = 24)
	private String status = "DRAFT";
	@Column(name = "published_at")
	private LocalDateTime publishedAt;
}
