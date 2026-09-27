package com.chronos.education.scheduling.model;

import java.time.LocalDateTime;
import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name="edu_exam_accommodation")
@Getter @Setter
public class ExamAccommodation extends BaseEntity {
	@Column(name="registration_id", nullable=false, length=64) private String registrationId;
	@Column(name="type_code", nullable=false, length=64) private String typeCode;
	@Column(name="extra_minutes", nullable=false) private Integer extraMinutes = 0;
	@Column(name="room_requirement_json", columnDefinition="text") private String roomRequirementJson;
	@Column(name="file_id", length=64) private String fileId;
	@Column(nullable=false, length=24) private String status = "SUBMITTED";
	@Column(name="decided_by", length=128) private String decidedBy;
	@Column(name="decided_at") private LocalDateTime decidedAt;
}
