package com.chronos.education.internship.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_internship_activity")
public class InternshipActivity extends BaseEntity {
	@Column(name = "placement_id", nullable = false, length = 64)
	private String placementId;

	@Column(name = "activity_type", nullable = false, length = 24)
	private String activityType;

	@Column(name = "activity_date", nullable = false)
	private LocalDate activityDate;

	@Column(nullable = false, length = 4000)
	private String content;

	@Column(length = 1000)
	private String result;

	@Column(name = "risk_level", length = 16)
	private String riskLevel;

	@Column(nullable = false, length = 24)
	private String status = "RECORDED";
}
