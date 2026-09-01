package com.chronos.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "t_portal_widget", comment = "门户组件")
public class PortalWidget extends BaseEntity {
    @Column(name = "widget_code", length = 80, nullable = false, unique = true)
    private String widgetCode;
    @Column(name = "widget_name", length = 120, nullable = false)
    private String widgetName;
    @Column(name = "description", length = 500)
    private String description;
    @Column(name = "component_name", length = 120, nullable = false)
    private String componentName;
    @Column(name = "provider_code", length = 80, nullable = false)
    private String providerCode;
    @Column(name = "required_permission", length = 200)
    private String requiredPermission;
    @Column(name = "default_size", length = 20, nullable = false)
    private String defaultSize = "MEDIUM";
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
    @Column(name = "audience_role_codes", length = 1000) private String audienceRoleCodes;
    @Column(name = "audience_organization_ids", length = 2000) private String audienceOrganizationIds;
    @Column(name = "audience_department_ids", length = 4000) private String audienceDepartmentIds;
}
