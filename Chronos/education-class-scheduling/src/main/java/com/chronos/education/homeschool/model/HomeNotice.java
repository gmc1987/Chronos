package com.chronos.education.homeschool.model;

import java.time.LocalDateTime;
import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter @NoArgsConstructor
@Table(name = "edu_home_notice")
public class HomeNotice extends BaseEntity {
	@Column(name = "school_id", length = 64) private String schoolId;
	@Column(name = "class_id", length = 64, nullable = false) private String classId;
	@Column(name = "title", length = 200, nullable = false) private String title;
	@Column(name = "content", columnDefinition = "text", nullable = false) private String content;
	@Column(name = "receipt_required", nullable = false) private Boolean receiptRequired = false;
	@Column(name = "publish_at") private LocalDateTime publishAt;
	@Column(name = "expire_at") private LocalDateTime expireAt;
	@Column(name = "status", length = 24, nullable = false) private String status = "DRAFT";
	@Column(name = "publisher_username", length = 100) private String publisherUsername;
	@Version @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;
}
