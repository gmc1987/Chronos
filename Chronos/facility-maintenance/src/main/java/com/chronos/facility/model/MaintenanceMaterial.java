package com.chronos.facility.model;

import java.math.BigDecimal;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "fm_material", comment = "维修物料库存")
public class MaintenanceMaterial extends BaseEntity {
	@Column(name = "material_code", nullable = false, unique = true, length = 64)
	private String materialCode;
	@Column(name = "material_name", nullable = false, length = 200)
	private String materialName;
	@Column(name = "specification", length = 200)
	private String specification;
	@Column(name = "unit", nullable = false, length = 32)
	private String unit;
	@Column(name = "stock_quantity", nullable = false, precision = 18, scale = 3)
	private BigDecimal stockQuantity = BigDecimal.ZERO;
	@Column(name = "reserved_quantity", nullable = false, precision = 18, scale = 3)
	private BigDecimal reservedQuantity = BigDecimal.ZERO;
	@Column(name = "safety_stock", nullable = false, precision = 18, scale = 3)
	private BigDecimal safetyStock = BigDecimal.ZERO;
	@Column(name = "unit_cost", nullable = false, precision = 18, scale = 2)
	private BigDecimal unitCost = BigDecimal.ZERO;
	@Column(name = "status", nullable = false, length = 16)
	private String status = "ACTIVE";
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
