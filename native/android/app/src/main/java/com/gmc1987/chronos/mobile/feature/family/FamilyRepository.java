package com.gmc1987.chronos.mobile.feature.family;

import com.gmc1987.chronos.mobile.core.network.HttpClient;
import org.json.JSONObject;

public final class FamilyRepository {
    private final HttpClient http;

    public FamilyRepository(HttpClient http) { this.http = http; }
    public JSONObject children() throws Exception { return http.get("/portal/education/family/children"); }
    public JSONObject grades() throws Exception { return http.get("/portal/education/family/grades"); }
    public JSONObject notices() throws Exception { return http.get("/portal/education/family/notices"); }
    public JSONObject feedbacks() throws Exception { return http.get("/portal/education/family/feedback"); }
    public JSONObject receipt(String id) throws Exception {
        return http.post("/portal/education/family/notices/" + id + "/receipt", new JSONObject(), true);
    }
    public JSONObject submitFeedback(String studentId, String title, String content) throws Exception {
        return http.post("/portal/education/family/feedback", new JSONObject()
                .put("studentId", studentId).put("title", title).put("content", content), true);
    }
}
