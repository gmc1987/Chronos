package com.chronos.education.internship.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_internship_incident")
public class InternshipIncident extends BaseEntity {
	@Column(name = "placement_id", nullable = false, length = 64)
	private String placementId;

	@Column(name = "occurred_at", nullable = false)
	private LocalDateTime occurredAt;

	@Column(nullable = false, length = 16)
	private String severity;

	@Column(nullable = false, length = 4000)
	private String description;

	@Column(name = "emergency_action", nullable = false, length = 2000)
	private String emergencyAction;

	@Column(nullable = false, length = 24)
	private String status = "REPORTED";

	@Column(name = "reported_by", nullable = false, length = 128)
	private String reportedBy;

	@Column(name = "closed_by", length = 128)
	private String closedBy;

	@Column(name = "closed_at")
	private LocalDateTime closedAt;

	@Column(name = "closure_comment", length = 2000)
	private String closureComment;

	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
