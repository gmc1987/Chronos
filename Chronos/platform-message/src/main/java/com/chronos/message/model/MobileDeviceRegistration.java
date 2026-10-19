package com.chronos.message.model;

import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Encrypted APNs/FCM registration owned by one authenticated user installation. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "msg_mobile_device",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_msg_mobile_device_installation",
				columnNames = { "username", "platform", "installation_id" }))
public class MobileDeviceRegistration extends BaseEntity {
	@Column(name = "username", length = 128, nullable = false)
	private String username;

	@Column(name = "platform", length = 16, nullable = false)
	private String platform;

	@Column(name = "installation_id", length = 128, nullable = false)
	private String installationId;

	@Column(name = "provider_token_ciphertext", columnDefinition = "text", nullable = false)
	private String providerTokenCiphertext;

	@Column(name = "provider_token_fingerprint", length = 128, nullable = false)
	private String providerTokenFingerprint;

	@Column(name = "device_model", length = 128)
	private String deviceModel;

	@Column(name = "app_version", length = 64)
	private String appVersion;

	@Column(name = "enabled", nullable = false)
	private Boolean enabled = true;

	@Column(name = "last_seen_at", nullable = false)
	private LocalDateTime lastSeenAt;
}
