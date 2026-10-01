package com.chronos.education.scheduling.model;

import java.time.LocalDateTime;
import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name="edu_exam_incident_action")
@Getter @Setter
public class ExamIncidentAction extends BaseEntity {
	@Column(name="incident_id", nullable=false, length=64) private String incidentId;
	@Column(name="action_type", nullable=false, length=64) private String actionType;
	@Column(name="action_by", nullable=false, length=128) private String actionBy;
	@Column(name="action_at", nullable=false) private LocalDateTime actionAt;
	@Column(nullable=false, columnDefinition="text") private String conclusion;
	@Column(name="file_id", length=64) private String fileId;
}
