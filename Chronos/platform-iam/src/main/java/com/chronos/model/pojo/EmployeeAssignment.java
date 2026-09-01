package com.chronos.model.pojo;

import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Getter @Setter @NoArgsConstructor
@Table(name = "t_employee_assignment", comment = "员工任职关系")
public class EmployeeAssignment extends BaseEntity {
    @Column(name = "employee_id", length = 64, nullable = false) private String employeeId;
    @Column(name = "organization_id", length = 64, nullable = false) private String organizationId;
    @Column(name = "organization_unit_id", length = 64, nullable = false) private String organizationUnitId;
    @Column(name = "position_id", length = 64) private String positionId;
    @Column(name = "job_level_id", length = 64) private String jobLevelId;
    @Column(name = "job_title_id", length = 64) private String jobTitleId;
    @Column(name = "primary_assignment", nullable = false) private Boolean primaryAssignment = false;
    @Column(name = "department_leader", nullable = false) private Boolean departmentLeader = false;
    @Column(name = "effective_from") private LocalDate effectiveFrom;
    @Column(name = "effective_to") private LocalDate effectiveTo;
    @Column(name = "status", nullable = false) private Integer status = 1;
}
