package com.chronos.industry.dao;

import com.chronos.industry.model.IndustryTemplateInstallation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndustryTemplateInstallationRepository
        extends JpaRepository<IndustryTemplateInstallation, String> {

    Optional<IndustryTemplateInstallation> findByIndustryCode(String industryCode);
}
