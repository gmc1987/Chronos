package com.chronos.education.scheduling.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_error_review")
public class ErrorReview {
	@Id
	@Column(length = 64)
	private String id;
	@Column(name = "error_item_id", nullable = false, length = 64)
	private String errorItemId;
	@Column(name = "reviewer_id", nullable = false, length = 64)
	private String reviewerId;
	@Column(nullable = false, length = 24)
	private String status;
	@Column(columnDefinition = "text")
	private String note;
	@Column(name = "reviewed_at", nullable = false)
	private LocalDateTime reviewedAt;
}
