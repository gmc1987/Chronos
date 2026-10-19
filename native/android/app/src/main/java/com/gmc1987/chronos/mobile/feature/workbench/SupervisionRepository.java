package com.gmc1987.chronos.mobile.feature.workbench;

import com.gmc1987.chronos.mobile.core.network.HttpClient;
import org.json.JSONObject;

public final class SupervisionRepository {
    private final HttpClient http;
    public SupervisionRepository(HttpClient http) { this.http = http; }
    public JSONObject list() throws Exception { return http.get("/portal/education/supervision/tasks"); }
    public JSONObject teachingTasks() throws Exception {
        return http.get("/portal/education/supervision/teaching-tasks");
    }
    public JSONObject issueProof(String id) throws Exception {
        return http.post("/portal/education/supervision/teaching-tasks/" + id + "/proof",
                new JSONObject(), true);
    }
    public JSONObject accept(String id) throws Exception {
        return http.post("/portal/education/supervision/tasks/" + id + "/accept",
                new JSONObject(), true);
    }
    public JSONObject checkIn(String id, String signedProof) throws Exception {
        JSONObject proof = new JSONObject().put("providerId", "TEACHER_QR_V1")
                .put("signedProof", signedProof);
        return http.post("/portal/education/supervision/tasks/" + id + "/check-in",
                new JSONObject().put("proof", proof), true);
    }
    public JSONObject evaluationForms() throws Exception {
        return http.get("/portal/education/supervision/evaluation-forms");
    }
    public JSONObject evaluationFields(String formId) throws Exception {
        return http.get("/portal/education/supervision/evaluation-forms/" + formId + "/fields");
    }
    public JSONObject saveEvaluation(String id, String formId, JSONObject values) throws Exception {
        return http.post("/portal/education/supervision/tasks/" + id + "/form",
                new JSONObject().put("formTemplateId", formId)
                        .put("values", values).put("draft", false), true);
    }
    public JSONObject submitEvaluation(String id, String formId) throws Exception {
        return http.post("/portal/education/supervision/tasks/" + id + "/submit",
                new JSONObject().put("formTemplateId", formId), true);
    }
    public JSONObject complete(String id) throws Exception {
        return http.post("/portal/education/supervision/tasks/" + id + "/complete",
                new JSONObject(), true);
    }
}
