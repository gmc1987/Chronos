package com.chronos.model.pojo;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "iam_identity_source")
@Getter
@Setter
public class IdentitySource extends BaseEntity {
	@Column(name = "source_code", nullable = false, unique = true, length = 64)
	private String sourceCode;
	@Column(nullable = false, length = 128)
	private String name;
	@Column(name = "source_type", nullable = false, length = 24)
	private String sourceType;
	@Column(name = "issuer_url", length = 1000)
	private String issuerUrl;
	@Column(name = "client_id", length = 256)
	private String clientId;
	@Column(name = "secret_ref", length = 256)
	private String secretRef;
	@Column(nullable = false, length = 24)
	private String status = "DRAFT";
	@Column(name = "config_json", columnDefinition = "text", nullable = false)
	private String configJson = "{}";
	@Column(name = "last_test_at")
	private LocalDateTime lastTestAt;
}
