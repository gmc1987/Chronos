package com.chronos.model.pojo;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "iam_external_identity")
@Getter
@Setter
public class ExternalIdentity extends BaseEntity {
	@Column(name = "source_id", nullable = false, length = 64)
	private String sourceId;
	@Column(name = "external_subject", nullable = false, length = 256)
	private String externalSubject;
	@Column(name = "user_id", nullable = false, length = 64)
	private String userId;
	@Column(name = "employee_id", length = 64)
	private String employeeId;
	@Column(nullable = false, length = 24)
	private String status = "ACTIVE";
	@Column(name = "linked_at", nullable = false)
	private LocalDateTime linkedAt;
	@Column(name = "last_login_at")
	private LocalDateTime lastLoginAt;
}
