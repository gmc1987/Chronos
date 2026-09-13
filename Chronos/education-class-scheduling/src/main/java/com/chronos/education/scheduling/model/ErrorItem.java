package com.chronos.education.scheduling.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter @NoArgsConstructor
@Table(name = "edu_error_item")
public class ErrorItem {
	@Id @Column(length = 64) private String id;
	@Column(name = "book_id", nullable = false, length = 64) private String bookId;
	@Column(name = "question_id", length = 64) private String questionId;
	@Column(name = "source_ref", length = 64) private String sourceRef;
	@Column(name = "source_type", length = 32) private String sourceType;
	@Column(name = "source_item_id", length = 64) private String sourceItemId;
	@Column(columnDefinition = "text") private String analysis;
	@Column(nullable = false, length = 24) private String status = "OPEN";
	@Column(nullable = false) private int wrongCount = 1;
	@Column(name = "last_wrong_at") private java.time.LocalDateTime lastWrongAt;
	@Column(name = "mastery_status", nullable = false, length = 24) private String masteryStatus = "NEEDS_PRACTICE";
	@Column(name = "student_note", columnDefinition = "text") private String studentNote;
	@Column(name = "teacher_note", columnDefinition = "text") private String teacherNote;
	@Column(name = "create_time", nullable = false) private java.time.LocalDateTime createTime;
}
