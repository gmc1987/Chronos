package com.chronos.industry.service;

import com.chronos.industry.api.IndustryMetadata;
import com.chronos.industry.api.IndustryTemplateProvider;
import com.chronos.industry.config.IndustryProperties;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class IndustryTemplateRegistry {

    private final Map<String, IndustryTemplateProvider> providers;
    private final IndustryProperties properties;

    public IndustryTemplateRegistry(
            List<IndustryTemplateProvider> providers,
            IndustryProperties properties) {
        this.properties = properties;
        this.providers = new LinkedHashMap<>();
        providers.forEach(provider -> {
            String code = normalize(provider.metadata().code());
            if (this.providers.putIfAbsent(code, provider) != null) {
                throw new IllegalStateException("Duplicate industry template: " + code);
            }
        });
    }

    public IndustryMetadata active() {
        String activeCode = normalize(properties.getActive());
        IndustryTemplateProvider provider = providers.get(activeCode);
        if (provider == null) {
            throw new IllegalStateException(
                    "Industry template is not installed: " + activeCode
                            + ", available=" + providers.keySet());
        }
        return provider.metadata();
    }

    public List<IndustryMetadata> all() {
        return providers.values().stream()
                .map(IndustryTemplateProvider::metadata)
                .sorted(Comparator.comparing(IndustryMetadata::code))
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
