package com.chronos.education.supervision.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "edu_supervision_rectification")
@Getter
@Setter
@NoArgsConstructor
public class SupervisionRectification extends BaseEntity {
	@Column(name = "issue_id", nullable = false, unique = true, length = 64) private String issueId;
	@Column(name = "rectifier_id", nullable = false, length = 64) private String rectifierId;
	@Column(nullable = false, columnDefinition = "text") private String content;
	@Column(name = "submitted_at", nullable = false) private LocalDateTime submittedAt;
	@Column(nullable = false, length = 24) private String status = "SUBMITTED";
	@Version @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;
}
