package com.gmc1987.chronos.mobile.feature.exams;

import com.gmc1987.chronos.mobile.core.network.HttpClient;
import org.json.JSONObject;

public final class ExamsRepository {
    private final HttpClient http;
    public ExamsRepository(HttpClient http) { this.http = http; }
    public JSONObject list() throws Exception { return http.get("/portal/education/exam/my-exams"); }
    public JSONObject invigilations() throws Exception {
        return http.get("/portal/education/exam/my-invigilations");
    }
    public JSONObject acknowledge(String id) throws Exception {
        return http.post("/portal/education/exam/my-invigilations/" + id + "/acknowledge",
                new JSONObject(), true);
    }
    public JSONObject checkIn(String id) throws Exception {
        return http.post("/portal/education/exam/my-invigilations/" + id + "/check-in",
                new JSONObject(), true);
    }
    public JSONObject requestChange(String id, String reason) throws Exception {
        return http.post("/portal/education/exam/my-invigilations/" + id + "/change-requests",
                new JSONObject().put("reason", reason), true);
    }
}
