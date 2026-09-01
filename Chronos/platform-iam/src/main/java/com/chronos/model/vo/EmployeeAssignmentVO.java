package com.chronos.model.vo;
import java.time.LocalDate;
import lombok.Data;
@Data
public class EmployeeAssignmentVO {
    private String id; private String employeeId; private String employeeName; private String organizationId; private String organizationName;
    private String departmentId; private String departmentName; private String positionId; private String positionName;
    private String jobLevelId; private String jobLevelName; private String jobTitleId; private String jobTitleName;
    private Boolean primaryAssignment; private Boolean departmentLeader; private LocalDate effectiveFrom; private LocalDate effectiveTo; private Integer status;
}
