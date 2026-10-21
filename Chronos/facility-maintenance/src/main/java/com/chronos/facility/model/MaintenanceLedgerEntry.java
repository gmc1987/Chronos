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
@Table(name = "fm_ledger_entry", comment = "维修操作与物料台账")
public class MaintenanceLedgerEntry extends BaseEntity {
	@Column(name = "repair_order_id", length = 64)
	private String repairOrderId;
	@Column(name = "material_id", length = 64)
	private String materialId;
	@Column(name = "entry_type", nullable = false, length = 32)
	private String entryType;
	@Column(name = "quantity", precision = 18, scale = 3)
	private BigDecimal quantity;
	@Column(name = "amount", precision = 18, scale = 2)
	private BigDecimal amount;
	@Column(name = "operator_username", nullable = false, length = 100)
	private String operatorUsername;
	@Column(name = "description", nullable = false, length = 1000)
	private String description;
}
