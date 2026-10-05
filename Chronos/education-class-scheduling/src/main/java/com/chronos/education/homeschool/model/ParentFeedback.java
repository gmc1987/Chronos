package com.chronos.education.homeschool.model;

import java.time.LocalDateTime;
import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter @NoArgsConstructor
@Table(name = "edu_parent_feedback")
public class ParentFeedback extends BaseEntity {
	@Column(name = "parent_id", nullable = false, length = 64) private String parentId;
	@Column(name = "student_id", nullable = false, length = 64) private String studentId;
	@Column(name = "class_id", nullable = false, length = 64) private String classId;
	@Column(name = "title", nullable = false, length = 200) private String title;
	@Column(name = "content", nullable = false, columnDefinition = "text") private String content;
	@Column(name = "status", nullable = false, length = 32) private String status = "SUBMITTED";
	@Column(name = "assigned_to", length = 100) private String assignedTo;
	@Column(name = "staff_reply", columnDefinition = "text") private String staffReply;
	@Column(name = "replied_at") private LocalDateTime repliedAt;
	@Column(name = "due_at") private LocalDateTime dueAt;
	@Column(name = "accepted_at") private LocalDateTime acceptedAt;
	@Column(name = "resolved_at") private LocalDateTime resolvedAt;
	@Column(name = "closed_at") private LocalDateTime closedAt;
	@Column(name = "parent_confirmed_at") private LocalDateTime parentConfirmedAt;
	@Column(name = "reopened_at") private LocalDateTime reopenedAt;
	@Version @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;
}
