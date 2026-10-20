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

/** One-time invitation issued by an authenticated student to establish a guardian relation. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_guardian_binding_invitation")
public class GuardianBindingInvitation extends BaseEntity {
	@Column(name = "scene_code", length = 32, nullable = false, unique = true)
	private String sceneCode;

	@Column(name = "organization_id", length = 64, nullable = false)
	private String organizationId;

	@Column(name = "student_id", length = 64, nullable = false)
	private String studentId;

	@Column(name = "relationship", length = 32, nullable = false)
	private String relationship;

	@Column(name = "primary_guardian", nullable = false)
	private Boolean primaryGuardian = false;

	@Column(name = "emergency_contact", nullable = false)
	private Boolean emergencyContact = false;

	@Column(name = "issued_by", length = 100, nullable = false)
	private String issuedBy;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "consumed_at")
	private LocalDateTime consumedAt;

	@Column(name = "consumed_by", length = 100)
	private String consumedBy;

	@Column(name = "status", length = 24, nullable = false)
	private String status = "PENDING";

	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
