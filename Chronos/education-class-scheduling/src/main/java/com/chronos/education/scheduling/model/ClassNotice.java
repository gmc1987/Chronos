package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 班级家校通知主表；发布后正文冻结，接收范围以收件人快照为准。 */
@Entity @Getter @Setter @NoArgsConstructor
@Table(name = "edu_class_notice")
public class ClassNotice extends BaseEntity {
	@Column(name = "class_id", nullable = false, length = 64) private String classId;
	@Column(name = "title", nullable = false, length = 200) private String title;
	@Column(name = "content", nullable = false, columnDefinition = "text") private String content;
	@Column(name = "require_receipt", nullable = false) private Boolean requireReceipt = true;
	@Column(name = "receipt_deadline") private LocalDateTime receiptDeadline;
	@Column(name = "status", nullable = false, length = 24) private String status = "DRAFT";
	@Column(name = "publisher_username", nullable = false, length = 128) private String publisherUsername;
	@Column(name = "published_at") private LocalDateTime publishedAt;
	@Version @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;
}
