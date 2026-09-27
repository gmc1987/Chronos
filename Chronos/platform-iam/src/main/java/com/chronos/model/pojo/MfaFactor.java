package com.chronos.model.pojo;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "iam_mfa_factor")
@Getter
@Setter
public class MfaFactor extends BaseEntity {
	@Column(name = "user_id", nullable = false, length = 64)
	private String userId;
	@Column(name = "factor_type", nullable = false, length = 24)
	private String factorType = "TOTP";
	@Column(name = "secret_ciphertext", nullable = false, columnDefinition = "text")
	private String secretCiphertext;
	@Column(name = "recovery_code_hashes", nullable = false, columnDefinition = "text")
	private String recoveryCodeHashes = "[]";
	@Column(nullable = false, length = 24)
	private String status = "PENDING";
	@Column(name = "enrolled_at", nullable = false)
	private LocalDateTime enrolledAt;
	@Column(name = "verified_at")
	private LocalDateTime verifiedAt;
}
