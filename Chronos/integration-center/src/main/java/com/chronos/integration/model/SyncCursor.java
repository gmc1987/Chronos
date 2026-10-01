package com.chronos.integration.model;

import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "int_sync_cursor")
@Getter
@Setter
public class SyncCursor extends BaseEntity {
	@Column(name = "job_id", nullable = false, unique = true, length = 64)
	private String jobId;
	@Column(name = "cursor_value", columnDefinition = "text")
	private String cursorValue;
	@Column(name = "last_success_at")
	private LocalDateTime lastSuccessAt;
	@Version
	@Column(name = "version_no", nullable = false)
	private Long versionNo = 0L;
}
