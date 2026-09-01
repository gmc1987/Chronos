package com.chronos.model.domain;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 能力定义实体。
 * <p>
 * 能力是“资源可做什么”的标准化抽象，支持技能、资质、证书、设备特性等。
 */
@SuppressWarnings("serial")
@Entity
@DynamicUpdate
@DynamicInsert
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "d_capability", comment = "领域-资源能力")
public class Capability extends BaseEntity {

	/** 所属组织。 */
	@Column(name = "organization_id", nullable = false, length = 64)
	private String organizationId;

	/** 能力名称。 */
	@Column(name = "capability_name", length = 100, nullable = false)
	private String capabilityName;

	/** 能力编码。 */
	@Column(name = "capability_code", length = 100, nullable = false)
	private String capabilityCode;

	/** 能力分类。 */
	@Column(name = "capability_category", length = 100)
	private String capabilityCategory;

	/** 是否要求资质证明。 */
	@Column(name = "certification_required", nullable = false)
	private Boolean certificationRequired = false;

	/** 是否启用。 */
	@Column(name = "active", nullable = false)
	private Boolean active = true;

	/** 描述。 */
	@Lob
	@Column(name = "description")
	private String description;

	/** 启用能力定义。 */
	public void activate() {
		this.active = true;
	}

	/** 停用能力定义。 */
	public void deactivate() {
		this.active = false;
	}

	/**
	 * 重命名能力。
	 *
	 * @param newName 新名称
	 */
	public void rename(String newName) {
		if (newName == null || newName.isBlank()) {
			throw new IllegalArgumentException("capabilityName 不能为空");
		}
		this.capabilityName = newName.trim();
	}
}
