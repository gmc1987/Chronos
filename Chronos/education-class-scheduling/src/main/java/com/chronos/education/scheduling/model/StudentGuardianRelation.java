package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 学生与家长为多对多关系，主监护人和紧急联系人属于关系属性。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "edu_student_guardian",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_edu_student_guardian",
				columnNames = { "student_id", "parent_id" }))
public class StudentGuardianRelation extends BaseEntity {
	@Column(name = "student_id", length = 64, nullable = false)
	private String studentId;

	@Column(name = "parent_id", length = 64, nullable = false)
	private String parentId;

	@Column(name = "relationship", length = 32, nullable = false)
	private String relationship;

	@Column(name = "primary_guardian", nullable = false)
	private Boolean primaryGuardian = false;

	@Column(name = "emergency_contact", nullable = false)
	private Boolean emergencyContact = false;

	@Column(name = "verification_status", length = 24, nullable = false)
	private String verificationStatus = "PENDING";

	@Column(name = "verification_note", length = 1000)
	private String verificationNote;

	@Column(name = "verified_by", length = 128)
	private String verifiedBy;

	@Column(name = "verified_at")
	private LocalDateTime verifiedAt;

	@Column(name = "restriction_level", length = 24, nullable = false)
	private String restrictionLevel = "NONE";

	@Column(name = "restriction_reason", length = 1000)
	private String restrictionReason;

	@Column(name = "restriction_from")
	private LocalDate restrictionFrom;

	@Column(name = "restriction_to")
	private LocalDate restrictionTo;

	@Column(name = "student_consent_status", length = 24, nullable = false)
	private String studentConsentStatus = "NOT_REQUIRED";

	@Column(name = "student_consented_at")
	private LocalDateTime studentConsentedAt;

	@Column(name = "consent_expires_at")
	private LocalDateTime consentExpiresAt;

	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;

	/** 只有已核验、未禁止访问且成年人授权有效的关系才能读取学生业务数据。 */
	@Transient
	public boolean isAccessAllowed() {
		LocalDate today = LocalDate.now();
		boolean restrictionActive = restrictionFrom == null || !restrictionFrom.isAfter(today);
		boolean restrictionNotExpired = restrictionTo == null || !restrictionTo.isBefore(today);
		boolean restricted = restrictionActive && restrictionNotExpired
				&& ("NO_ACCESS".equals(restrictionLevel) || "CONTACT_ONLY".equals(restrictionLevel));
		boolean consentValid = "NOT_REQUIRED".equals(studentConsentStatus)
				|| ("GRANTED".equals(studentConsentStatus)
						&& (consentExpiresAt == null || consentExpiresAt.isAfter(LocalDateTime.now())));
		return "VERIFIED".equals(verificationStatus) && !restricted && consentValid;
	}
}
