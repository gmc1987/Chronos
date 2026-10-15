package com.gmc1987.chronos.mobile.feature.workbench;

import com.gmc1987.chronos.mobile.core.network.HttpClient;
import org.json.JSONObject;

public final class SupervisionRepository {
    private final HttpClient http;
    public SupervisionRepository(HttpClient http) { this.http = http; }
    public JSONObject list() throws Exception { return http.get("/portal/education/supervision/tasks"); }
}
