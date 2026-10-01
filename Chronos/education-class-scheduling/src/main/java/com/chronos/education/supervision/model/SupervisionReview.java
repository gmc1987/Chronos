package com.chronos.education.supervision.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "edu_supervision_review")
@Getter
@Setter
@NoArgsConstructor
public class SupervisionReview extends BaseEntity {
	@Column(name = "issue_id", nullable = false, length = 64) private String issueId;
	@Column(name = "reviewer_id", nullable = false, length = 64) private String reviewerId;
	@Column(nullable = false, length = 16) private String decision;
	@Column(columnDefinition = "text") private String comment;
	@Column(name = "reviewed_at", nullable = false) private LocalDateTime reviewedAt;
}
