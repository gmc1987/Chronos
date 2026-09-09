package com.chronos.industry.service;

import com.chronos.industry.api.IndustryMetadata;
import com.chronos.industry.dao.IndustryTemplateInstallationRepository;
import com.chronos.industry.model.IndustryTemplateInstallation;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndustryTemplateInstallationService {

    private final IndustryTemplateInstallationRepository installations;

    public IndustryTemplateInstallationService(
            IndustryTemplateInstallationRepository installations) {
        this.installations = installations;
    }

    /**
     * 这里只记录模板安装版本，不覆盖用户已经调整的菜单、流程和表单。
     * 后续模板升级必须通过独立的、可审计的迁移器执行。
     */
    @Transactional
    public void activate(IndustryMetadata metadata) {
        installations.findAll().forEach(installation -> {
            installation.setActive(
                    installation.getIndustryCode().equalsIgnoreCase(metadata.code()));
            installations.save(installation);
        });

        IndustryTemplateInstallation installation = installations
                .findByIndustryCode(metadata.code())
                .orElseGet(IndustryTemplateInstallation::new);
        boolean versionChanged = installation.getInstalledVersion() == null
                || !installation.getInstalledVersion().equals(metadata.version());
        installation.setIndustryCode(metadata.code());
        installation.setInstalledVersion(metadata.version());
        if (versionChanged || installation.getInstalledAt() == null) {
            installation.setInstalledAt(LocalDateTime.now());
        }
        installation.setActive(true);
        installations.save(installation);
    }
}
