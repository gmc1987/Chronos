package com.chronos.model.dto;
import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data
public class EmployeeAssignmentDTO {
    private String id;
    @NotBlank private String employeeId;
    @NotBlank private String organizationId;
    @NotBlank private String departmentId;
    @NotBlank private String positionId;
    private String jobLevelId;
    private String jobTitleId;
    private Boolean primaryAssignment;
    private Boolean departmentLeader;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Integer status;
}
