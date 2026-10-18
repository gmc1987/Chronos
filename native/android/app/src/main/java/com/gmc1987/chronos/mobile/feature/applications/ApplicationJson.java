package com.gmc1987.chronos.mobile.feature.applications;

import org.json.JSONArray;
import org.json.JSONObject;

/** Shared envelope parsing for the native application screens. */
final class ApplicationJson {
    private ApplicationJson() {}

    static JSONObject dataObject(JSONObject envelope) {
        JSONObject data = envelope.optJSONObject("data");
        return data == null ? envelope : data;
    }

    static JSONArray rows(JSONObject envelope) {
        Object data = envelope.opt("data");
        if (data instanceof JSONArray) return (JSONArray) data;
        JSONObject object = data instanceof JSONObject ? (JSONObject) data : envelope;
        for (String key : new String[]{"content", "items", "records", "data"}) {
            JSONArray result = object.optJSONArray(key);
            if (result != null) return result;
        }
        return new JSONArray();
    }

    static String message(Exception error) {
        return error.getMessage() == null ? "请求失败，请稍后重试" : error.getMessage();
    }
}
