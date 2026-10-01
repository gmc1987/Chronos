package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 发布时冻结学生、家长和账号关系，后续解绑不改变历史送达证据。 */
@Entity @Getter @Setter @NoArgsConstructor
@Table(name = "edu_class_notice_recipient", uniqueConstraints = @UniqueConstraint(
		name = "uk_class_notice_recipient", columnNames = { "notice_id", "student_id", "parent_id" }))
public class ClassNoticeRecipient extends BaseEntity {
	@Column(name = "notice_id", nullable = false, length = 64) private String noticeId;
	@Column(name = "student_id", nullable = false, length = 64) private String studentId;
	@Column(name = "parent_id", nullable = false, length = 64) private String parentId;
	@Column(name = "recipient_username", length = 128) private String recipientUsername;
	@Column(name = "delivered_at") private LocalDateTime deliveredAt;
	@Column(name = "read_at") private LocalDateTime readAt;
	@Column(name = "acknowledged_at") private LocalDateTime acknowledgedAt;
	@Column(name = "acknowledgement", length = 500) private String acknowledgement;
}
