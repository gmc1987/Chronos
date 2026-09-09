package com.chronos.industry.config;

import com.chronos.industry.service.IndustryTemplateInstallationService;
import com.chronos.industry.service.IndustryTemplateRegistry;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IndustryProperties.class)
public class IndustryTemplateConfiguration {

    @Bean
    ApplicationRunner industryTemplateInstaller(
            IndustryTemplateRegistry registry,
            IndustryTemplateInstallationService installations) {
        return args -> installations.activate(registry.active());
    }
}
