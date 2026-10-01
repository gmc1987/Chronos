package com.chronos.education.scheduling.model;

import java.time.LocalDateTime;
import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name="edu_exam_incident")
@Getter @Setter
public class ExamIncident extends BaseEntity {
	@Column(name="session_id", nullable=false, length=64) private String sessionId;
	@Column(name="room_id", length=64) private String roomId;
	@Column(name="candidate_id", length=64) private String candidateId;
	@Column(name="incident_type", nullable=false, length=64) private String incidentType;
	@Column(nullable=false, length=24) private String severity;
	@Column(nullable=false, length=24) private String status = "REPORTED";
	@Column(nullable=false, columnDefinition="text") private String description;
	@Column(name="reported_by", nullable=false, length=128) private String reportedBy;
	@Column(name="reported_at", nullable=false) private LocalDateTime reportedAt;
	@Column(name="closed_at") private LocalDateTime closedAt;
	@Column(columnDefinition="text") private String conclusion;
}
