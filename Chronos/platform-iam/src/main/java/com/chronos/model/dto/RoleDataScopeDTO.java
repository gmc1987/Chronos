package com.chronos.model.dto;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RoleDataScopeDTO implements Serializable {
    private String scopeType;
    private String organizationId;
    private String organizationUnitId;
    private String employeeId;
}
