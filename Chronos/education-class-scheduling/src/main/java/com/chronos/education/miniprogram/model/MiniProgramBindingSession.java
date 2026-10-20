package com.chronos.education.miniprogram.model;

import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Short-lived server-side session used before an OpenID is bound to a platform account. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_mini_program_binding_session")
public class MiniProgramBindingSession extends BaseEntity {
	@Column(name = "token_hash", length = 64, nullable = false, unique = true)
	private String tokenHash;

	@Column(name = "identity_source", length = 64, nullable = false)
	private String identitySource;

	@Column(name = "open_id", length = 128, nullable = false)
	private String openId;

	@Column(name = "union_id", length = 128)
	private String unionId;

	@Column(name = "organization_id", length = 64)
	private String organizationId;

	@Column(name = "guardian_invitation_id", length = 64)
	private String guardianInvitationId;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "consumed_at")
	private LocalDateTime consumedAt;

	@Column(name = "status", length = 24, nullable = false)
	private String status = "PENDING";

	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
