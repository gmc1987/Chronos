package com.chronos.industry.api;

import java.util.List;

public record IndustryMetadata(
        String code,
        String name,
        String version,
        IndustryBranding branding,
        List<OrganizationTypeDefinition> organizationTypes,
        List<DirectoryOptionDefinition> departmentTypes,
        List<DirectoryOptionDefinition> positionCategories,
        List<IndustryFeatureDefinition> features,
        List<IndustryNavigationDefinition> navigation) {

    public IndustryMetadata {
        organizationTypes = List.copyOf(organizationTypes);
        departmentTypes = List.copyOf(departmentTypes);
        positionCategories = List.copyOf(positionCategories);
        features = List.copyOf(features);
        navigation = List.copyOf(navigation);
    }
}
