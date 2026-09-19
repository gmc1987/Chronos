package com.chronos.integration.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Encrypted connector secret boundary. The ciphertext is never exposed by an
 * API; writes must go through PlatformSecretCipher.
 */
@Entity
@Table(name = "int_credential")
@Getter
@Setter
public class Credential extends BaseEntity {
	@Column(name = "connector_id", nullable = false, length = 64)
	private String connectorId;

	@Column(name = "key_name", nullable = false, length = 120)
	private String keyName;

	@Column(name = "secret_ciphertext", nullable = false, columnDefinition = "text")
	private String secretCiphertext;
}
