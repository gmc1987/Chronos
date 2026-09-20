package com.chronos.education.supervision.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "edu_supervision_plan")
@Getter
@Setter
@NoArgsConstructor
public class SupervisionPlan extends BaseEntity {
	@Column(name = "school_id", nullable = false, length = 64) private String schoolId;
	@Column(name = "campus_id", length = 64) private String campusId;
	@Column(nullable = false, length = 200) private String name;
	@Column(nullable = false, length = 24) private String status = "DRAFT";
	@Column(name = "start_date", nullable = false) private LocalDate startDate;
	@Column(name = "end_date", nullable = false) private LocalDate endDate;
	@Version @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;
}
