package com.gmc1987.chronos.mobile.feature.workbench;

import com.gmc1987.chronos.mobile.core.network.HttpClient;
import org.json.JSONObject;

public final class MeetingsRepository {
    private final HttpClient http;
    public MeetingsRepository(HttpClient http) { this.http = http; }
    public JSONObject list() throws Exception { return http.get("/portal/education/meetings"); }
    public JSONObject respond(String id, String status, String comment) throws Exception {
        return http.post("/portal/education/meetings/" + id + "/response",
                new JSONObject().put("status", status).put("comment", comment), true);
    }
    public JSONObject checkIn(String id) throws Exception {
        return http.post("/portal/education/meetings/" + id + "/check-in", new JSONObject(), true);
    }
}
