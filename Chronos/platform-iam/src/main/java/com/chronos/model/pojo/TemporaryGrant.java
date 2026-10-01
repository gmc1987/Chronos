package com.chronos.model.pojo;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "iam_temporary_grant")
@Getter
@Setter
public class TemporaryGrant extends BaseEntity {
	@Column(name = "user_id", nullable = false, length = 64)
	private String userId;
	@Column(name = "permission_code", nullable = false, length = 256)
	private String permissionCode;
	@Column(nullable = false, length = 1000)
	private String reason;
	@Column(name = "requested_by", nullable = false, length = 128)
	private String requestedBy;
	@Column(name = "approved_by", length = 128)
	private String approvedBy;
	@Column(name = "second_approved_by", length = 128)
	private String secondApprovedBy;
	@Column(name = "valid_from", nullable = false)
	private LocalDateTime validFrom;
	@Column(name = "valid_until", nullable = false)
	private LocalDateTime validUntil;
	@Column(nullable = false, length = 24)
	private String status = "REQUESTED";
	@Column(name = "revoked_at")
	private LocalDateTime revokedAt;
}
