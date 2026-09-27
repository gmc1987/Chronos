package com.chronos.education.homeschool.model;

import java.time.LocalDateTime;
import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter @NoArgsConstructor
@Table(name = "edu_communication_record")
public class CommunicationRecord extends BaseEntity {
	@Column(name = "teacher_username", nullable = false, length = 100) private String teacherUsername;
	@Column(name = "student_id", nullable = false, length = 64) private String studentId;
	@Column(name = "class_id", nullable = false, length = 64) private String classId;
	@Column(name = "channel", nullable = false, length = 32) private String channel;
	@Column(name = "subject", length = 200) private String subject;
	@Column(name = "content", nullable = false, columnDefinition = "text") private String content;
	@Column(name = "sensitive_content", columnDefinition = "text") private String sensitiveContent;
	@Column(name = "occurred_at", nullable = false) private LocalDateTime occurredAt;
	@Version @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;
}
