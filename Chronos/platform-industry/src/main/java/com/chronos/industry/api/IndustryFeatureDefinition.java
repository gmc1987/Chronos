package com.chronos.industry.api;

public record IndustryFeatureDefinition(
        String code,
        String name,
        boolean enabledByDefault) {
}
