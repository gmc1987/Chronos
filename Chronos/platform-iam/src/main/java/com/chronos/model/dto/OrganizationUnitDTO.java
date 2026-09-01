package com.chronos.model.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data
public class OrganizationUnitDTO {
    private String id;
    @NotBlank private String organizationId;
    @NotBlank private String departmentCode;
    @NotBlank private String departmentName;
    private String parentId;
    private String departmentType;
    private String leaderEmployeeId;
    private Integer sortOrder;
    private Integer status;
    private String description;
}
