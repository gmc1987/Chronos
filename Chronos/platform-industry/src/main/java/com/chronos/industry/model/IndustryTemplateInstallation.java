package com.chronos.industry.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "sys_industry_template_installation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_industry_template_code",
                columnNames = "industry_code"))
public class IndustryTemplateInstallation extends BaseEntity {

    @Column(name = "industry_code", length = 32, nullable = false)
    private String industryCode;

    @Column(name = "installed_version", length = 32, nullable = false)
    private String installedVersion;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "installed_at", nullable = false)
    private LocalDateTime installedAt;

    public String getIndustryCode() {
        return industryCode;
    }

    public void setIndustryCode(String industryCode) {
        this.industryCode = industryCode;
    }

    public String getInstalledVersion() {
        return installedVersion;
    }

    public void setInstalledVersion(String installedVersion) {
        this.installedVersion = installedVersion;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getInstalledAt() {
        return installedAt;
    }

    public void setInstalledAt(LocalDateTime installedAt) {
        this.installedAt = installedAt;
    }
}
