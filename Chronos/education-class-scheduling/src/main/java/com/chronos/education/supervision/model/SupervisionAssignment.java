package com.chronos.education.supervision.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "edu_supervision_assignment")
@Getter
@Setter
@NoArgsConstructor
public class SupervisionAssignment extends BaseEntity {
	@Column(name = "plan_id", nullable = false, length = 64) private String planId;
	@Column(name = "school_id", nullable = false, length = 64) private String schoolId;
	@Column(name = "campus_id", length = 64) private String campusId;
	@Column(name = "supervisor_id", nullable = false, length = 64) private String supervisorId;
	@Column(name = "teacher_id", nullable = false, length = 64) private String teacherId;
	@Column(name = "schedule_entry_id", nullable = false, length = 64) private String scheduleEntryId;
	@Column(nullable = false, length = 24) private String status = "PENDING";
	@Column(name = "accepted_at") private LocalDateTime acceptedAt;
	@Column(name = "checked_in_at") private LocalDateTime checkedInAt;
	@Column(name = "submitted_at") private LocalDateTime submittedAt;
	@Column(name = "completed_at") private LocalDateTime completedAt;
	@Version @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;
}
