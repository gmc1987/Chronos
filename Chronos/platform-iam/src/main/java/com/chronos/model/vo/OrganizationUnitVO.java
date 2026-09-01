package com.chronos.model.vo;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
@Data
public class OrganizationUnitVO {
    private String id; private String organizationId; private String departmentCode; private String departmentName;
    private String parentId; private String departmentType; private String leaderEmployeeId; private String leaderEmployeeName;
    private Integer level; private String treePath; private Integer sortOrder; private Integer status; private String description;
    private List<OrganizationUnitVO> children = new ArrayList<>();
}
