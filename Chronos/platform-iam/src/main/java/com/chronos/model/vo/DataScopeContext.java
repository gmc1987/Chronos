package com.chronos.model.vo;
import java.util.Set;
public record DataScopeContext(boolean fullAccess, String employeeId, Set<String> organizationIds,
        Set<String> organizationUnitIds, Set<String> employeeIds) {}
