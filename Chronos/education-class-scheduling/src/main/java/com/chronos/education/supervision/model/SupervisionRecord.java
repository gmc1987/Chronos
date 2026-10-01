package com.chronos.education.supervision.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "edu_supervision_record")
@Getter
@Setter
@NoArgsConstructor
public class SupervisionRecord extends BaseEntity {
	@Column(name = "assignment_id", nullable = false, unique = true, length = 64) private String assignmentId;
	@Column(name = "school_id", nullable = false, length = 64) private String schoolId;
	@Column(name = "supervisor_id", nullable = false, length = 64) private String supervisorId;
	@Column(name = "teacher_id", nullable = false, length = 64) private String teacherId;
	@Column(name = "form_template_id", nullable = false, length = 64) private String formTemplateId;
	@Column(name = "form_snapshot_json", nullable = false, columnDefinition = "text") private String formSnapshotJson;
	@Column(name = "schedule_context_snapshot_json", nullable = false, columnDefinition = "text") private String scheduleContextSnapshotJson;
	@Column(name = "submitted_at", nullable = false) private LocalDateTime submittedAt;
	@Version @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;
}
