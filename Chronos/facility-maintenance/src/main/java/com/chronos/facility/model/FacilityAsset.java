package com.chronos.facility.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "fm_asset")
public class FacilityAsset extends BaseEntity {
	@Column(name = "asset_code", nullable = false, unique = true, length = 64)
	private String assetCode;
	@Column(name = "asset_name", nullable = false, length = 200)
	private String assetName;
	@Column(nullable = false, length = 64)
	private String category;
	@Column(name = "serial_number", length = 128)
	private String serialNumber;
	@Column(name = "organization_id", length = 64)
	private String organizationId;
	@Column(nullable = false, length = 300)
	private String location;
	@Column(name = "qr_token", nullable = false, unique = true, length = 64)
	private String qrToken;
	@Column(nullable = false, length = 24)
	private String status = "IN_SERVICE";
	@Column(name = "purchase_date")
	private LocalDate purchaseDate;
	@Column(name = "warranty_expiry")
	private LocalDate warrantyExpiry;
	@Column(name = "next_maintenance_date")
	private LocalDate nextMaintenanceDate;
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
