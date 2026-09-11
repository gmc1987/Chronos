package com.chronos.ai.model;

import com.chronos.model.pojo.BaseEntity;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** AI 模型的可持久化配置。配置只描述模型，不参与实际调用链。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "ai_model_config")
public class AiModel extends BaseEntity {
	@NotBlank
	@Size(max = 128)
	@Column(name = "model_name", length = 128, nullable = false)
	private String modelName;

	@Size(max = 64)
	@Column(name = "version", length = 64)
	private String version;

	@NotBlank
	@Size(max = 64)
	@Column(name = "model_type", length = 64, nullable = false)
	private String modelType;

	@NotBlank
	@Size(max = 64)
	@Column(name = "provider", length = 64, nullable = false)
	private String provider;

	/**
	 * The key is accepted when a command is deserialized, but is never included
	 * when an entity is serialized as a response.
	 */
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	@Size(max = 512)
	@Column(name = "api_key", length = 512)
	private String apiKey;

	@Size(max = 255)
	@Column(name = "signature_handler", length = 255)
	private String signatureHandler;

	@Size(max = 255)
	@Column(name = "adapter_class", length = 255)
	private String adapterClass;

	@NotNull
	@Min(0)
	@Max(1)
	@Column(name = "status", nullable = false)
	private Integer status = 1;
}
