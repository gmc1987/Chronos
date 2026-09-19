package com.chronos.education.homeschool.model;

import java.time.LocalDateTime;
import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter @NoArgsConstructor
@Table(name = "edu_home_notice_target",
		uniqueConstraints = @UniqueConstraint(name = "uk_edu_home_notice_target", columnNames = {"notice_id", "student_id", "parent_id"}))
public class HomeNoticeTarget extends BaseEntity {
	@Column(name = "notice_id", length = 64, nullable = false) private String noticeId;
	@Column(name = "student_id", length = 64, nullable = false) private String studentId;
	@Column(name = "parent_id", length = 64, nullable = false) private String parentId;
	@Column(name = "delivery_status", length = 24, nullable = false) private String deliveryStatus = "DELIVERED";
	@Column(name = "read_at") private LocalDateTime readAt;
	@Column(name = "receipt_status", length = 24, nullable = false) private String receiptStatus = "PENDING";
	@Column(name = "receipt_at") private LocalDateTime receiptAt;
	@Column(name = "receipt_comment", columnDefinition = "text") private String receiptComment;
}
