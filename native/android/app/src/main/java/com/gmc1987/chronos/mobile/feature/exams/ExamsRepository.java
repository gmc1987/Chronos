package com.gmc1987.chronos.mobile.feature.exams;

import com.gmc1987.chronos.mobile.core.network.HttpClient;
import org.json.JSONObject;

public final class ExamsRepository {
    private final HttpClient http;
    public ExamsRepository(HttpClient http) { this.http = http; }
    public JSONObject list() throws Exception { return http.get("/portal/education/exam/my-exams"); }
}
