package com.chronos.education.homeschool.model;

import java.time.LocalDateTime;
import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter @NoArgsConstructor
@Table(name = "edu_parent_account_binding",
		uniqueConstraints = @UniqueConstraint(name = "uk_edu_parent_account_binding", columnNames = {"parent_id", "username"}))
public class ParentAccountBinding extends BaseEntity {
	@Column(name = "parent_id", length = 64, nullable = false) private String parentId;
	@Column(name = "username", length = 100, nullable = false) private String username;
	@Column(name = "status", length = 24, nullable = false) private String status = "ACTIVE";
	@Column(name = "verified_at") private LocalDateTime verifiedAt;
	@Column(name = "invalidated_at") private LocalDateTime invalidatedAt;
	@Version @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;
}
