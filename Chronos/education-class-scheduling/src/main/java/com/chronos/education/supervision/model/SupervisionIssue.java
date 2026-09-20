package com.chronos.education.supervision.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "edu_supervision_issue")
@Getter
@Setter
@NoArgsConstructor
public class SupervisionIssue extends BaseEntity {
	@Column(name = "record_id", nullable = false, length = 64) private String recordId;
	@Column(name = "school_id", nullable = false, length = 64) private String schoolId;
	@Column(nullable = false, length = 16) private String severity;
	@Column(nullable = false, length = 200) private String title;
	@Column(columnDefinition = "text") private String description;
	@Column(nullable = false, length = 24) private String status = "OPEN";
	@Column(name = "owner_id", length = 64) private String ownerId;
	@Column(name = "due_at") private LocalDateTime dueAt;
	@Version @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;
}
