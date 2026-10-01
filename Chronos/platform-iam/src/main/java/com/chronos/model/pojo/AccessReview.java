package com.chronos.model.pojo;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "iam_access_review")
@Getter
@Setter
public class AccessReview extends BaseEntity {
	@Column(nullable = false, length = 256)
	private String scope;
	@Column(nullable = false, length = 24)
	private String status = "DRAFT";
	@Column(name = "due_at", nullable = false)
	private LocalDateTime dueAt;
	@Column(nullable = false, length = 128)
	private String owner;
	@Column(name = "submitted_at")
	private LocalDateTime submittedAt;
	@Column(name = "completed_at")
	private LocalDateTime completedAt;
}
