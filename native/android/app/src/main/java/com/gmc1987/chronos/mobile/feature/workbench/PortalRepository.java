package com.gmc1987.chronos.mobile.feature.workbench;

import com.gmc1987.chronos.mobile.core.model.PortalState;
import com.gmc1987.chronos.mobile.core.network.HttpClient;

public final class PortalRepository {
    private final HttpClient http;

    public PortalRepository(HttpClient http) {
        this.http = http;
    }

    public PortalState bootstrap() throws Exception {
        return PortalState.from(http.get("/portal/bootstrap"));
    }
}
