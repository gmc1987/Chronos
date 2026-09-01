package com.chronos.portal.spi;

public interface PortalContributionProvider {
    String providerCode();
    PortalContribution load(String username);
}
