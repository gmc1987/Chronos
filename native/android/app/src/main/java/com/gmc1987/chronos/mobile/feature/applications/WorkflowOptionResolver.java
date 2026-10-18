package com.gmc1987.chronos.mobile.feature.applications;

import com.gmc1987.chronos.mobile.core.network.HttpClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Resolves dictionary and same-origin remote options before a native form is rendered. */
final class WorkflowOptionResolver {
    private WorkflowOptionResolver() {}

    static void resolve(HttpClient http, JSONArray fields) throws Exception {
        if (fields == null) return;
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.optJSONObject(i); if (field == null) continue;
            String raw = field.optString("optionsJson");
            if (raw.isEmpty() || raw.trim().startsWith("[")) continue;
            JSONObject config;
            try { config = new JSONObject(raw); } catch (Exception ignored) { continue; }
            String path = null;
            boolean dictionary = false;
            String code = config.optString("dictCode");
            if (!code.isEmpty()) {
                dictionary = true;
                path = "/dicts/list?dictCode="
                        + URLEncoder.encode(code, StandardCharsets.UTF_8.name());
            } else if ("REMOTE".equals(config.optString("source"))) {
                String url = config.optString("url");
                if (url.startsWith("/")) path = url;
            }
            if (path == null) continue;
            JSONArray source = ApplicationJson.rows(http.get(path));
            JSONArray options = new JSONArray();
            for (int n = 0; n < source.length(); n++) {
                JSONObject item = source.optJSONObject(n); if (item == null) continue;
                if (dictionary && item.has("status") && item.optInt("status") != 1) continue;
                String value = item.optString("value", item.optString("dictValue"));
                String label = item.optString("label", item.optString("dictName", value));
                if (!value.isEmpty()) options.put(new JSONObject().put("value", value).put("label", label));
            }
            field.put("optionsJson", options.toString());
        }
    }
}
