package com.chronos.model.pojo;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@Table(name = "t_portal_application", comment = "门户应用")
public class PortalApplication extends BaseEntity {
    @Column(name = "app_code", length = 80, nullable = false, unique = true)
    private String appCode;
    @Column(name = "app_name", length = 120, nullable = false)
    private String appName;
    @Column(name = "description", length = 500)
    private String description;
    @Column(name = "icon", length = 100)
    private String icon;
    @Column(name = "route_path", length = 300, nullable = false)
    private String routePath;
    @Column(name = "open_mode", length = 20, nullable = false)
    private String openMode = "INTERNAL";
    @Column(name = "required_permission", length = 200)
    private String requiredPermission;
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
    @Column(name = "recommended", nullable = false)
    private Boolean recommended = false;
    @Column(name = "audience_role_codes", length = 1000) private String audienceRoleCodes;
    @Column(name = "audience_organization_ids", length = 2000) private String audienceOrganizationIds;
    @Column(name = "audience_department_ids", length = 4000) private String audienceDepartmentIds;
}
