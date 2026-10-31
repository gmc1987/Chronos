package com.chronos.facility.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "fm_inventory_transaction")
public class InventoryTransaction extends BaseEntity {
	@Column(name = "material_id", nullable = false, length = 64)
	private String materialId;
	@Column(name = "transaction_type", nullable = false, length = 24)
	private String transactionType;
	@Column(nullable = false, precision = 18, scale = 3)
	private BigDecimal quantity;
	@Column(name = "before_quantity", nullable = false, precision = 18, scale = 3)
	private BigDecimal beforeQuantity;
	@Column(name = "after_quantity", nullable = false, precision = 18, scale = 3)
	private BigDecimal afterQuantity;
	@Column(name = "reference_type", length = 32)
	private String referenceType;
	@Column(name = "reference_id", length = 64)
	private String referenceId;
	@Column(name = "operator_username", nullable = false, length = 100)
	private String operatorUsername;
	@Column(nullable = false, length = 1000)
	private String reason;
	@Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
	private String idempotencyKey;
}
