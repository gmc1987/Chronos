package com.chronos.facility.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "fm_preventive_plan")
public class PreventiveMaintenancePlan extends BaseEntity {
	@Column(name = "asset_id", nullable = false, length = 64)
	private String assetId;
	@Column(name = "plan_name", nullable = false, length = 200)
	private String planName;
	@Column(name = "interval_days", nullable = false)
	private Integer intervalDays;
	@Column(name = "next_due_date", nullable = false)
	private LocalDate nextDueDate;
	@Column(name = "assigned_username", length = 100)
	private String assignedUsername;
	@Column(nullable = false, length = 4000)
	private String checklist;
	@Column(nullable = false, length = 24)
	private String status = "ACTIVE";
	@Column(name = "last_completed_at")
	private LocalDateTime lastCompletedAt;
	@Column(name = "last_completed_by", length = 100)
	private String lastCompletedBy;
	@Column(name = "completion_note", length = 2000)
	private String completionNote;
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
