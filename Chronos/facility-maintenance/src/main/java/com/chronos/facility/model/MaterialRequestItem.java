package com.chronos.facility.model;

import java.math.BigDecimal;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "fm_material_request_item", comment = "维修物料申请明细")
public class MaterialRequestItem extends BaseEntity {
	@Column(name = "request_id", nullable = false, length = 64)
	private String requestId;
	@Column(name = "material_id", nullable = false, length = 64)
	private String materialId;
	@Column(name = "material_code", nullable = false, length = 64)
	private String materialCode;
	@Column(name = "material_name", nullable = false, length = 200)
	private String materialName;
	@Column(name = "requested_quantity", nullable = false, precision = 18, scale = 3)
	private BigDecimal requestedQuantity;
	@Column(name = "approved_quantity", precision = 18, scale = 3)
	private BigDecimal approvedQuantity;
	@Column(name = "issued_quantity", precision = 18, scale = 3)
	private BigDecimal issuedQuantity;
}
