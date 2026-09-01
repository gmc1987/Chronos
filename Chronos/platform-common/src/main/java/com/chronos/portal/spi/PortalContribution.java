package com.chronos.portal.spi;

import java.util.Map;

public record PortalContribution(String providerCode, boolean available, String message, Map<String, Object> data) {
    public static PortalContribution unavailable(String providerCode, String message) {
        return new PortalContribution(providerCode, false, message, Map.of());
    }
}
