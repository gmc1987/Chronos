package com.gmc1987.chronos.mobile.feature.grades;

import com.gmc1987.chronos.mobile.core.network.HttpClient;
import org.json.JSONObject;

public final class GradesRepository {
    private final HttpClient http;
    public GradesRepository(HttpClient http) { this.http = http; }
    public JSONObject list() throws Exception { return http.get("/portal/education/grades"); }
}
