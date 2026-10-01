package com.chronos.model.pojo;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "iam_access_review_item")
@Getter
@Setter
public class AccessReviewItem extends BaseEntity {
	@Column(name = "review_id", nullable = false, length = 64)
	private String reviewId;
	@Column(name = "user_id", nullable = false, length = 64)
	private String userId;
	@Column(name = "role_id", length = 64)
	private String roleId;
	@Column(name = "permission_code", nullable = false, length = 256)
	private String permissionCode;
	@Column(length = 24)
	private String decision;
	@Column(name = "decided_by", length = 128)
	private String decidedBy;
	@Column(name = "decided_at")
	private LocalDateTime decidedAt;
}
