package com.chronos.industry.api;

public record IndustryNavigationDefinition(
        String code,
        String name,
        String route,
        String requiredPermission,
        int sortOrder) {
}
