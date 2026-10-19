package com.gmc1987.chronos.mobile.feature.announcements;

import com.gmc1987.chronos.mobile.core.network.HttpClient;
import org.json.JSONObject;

public final class AnnouncementsRepository {
    private final HttpClient http;
    public AnnouncementsRepository(HttpClient http) { this.http = http; }
    public JSONObject list(String type, String keyword, boolean unreadOnly, int page) throws Exception {
        StringBuilder path = new StringBuilder("/publications?page=").append(page)
                .append("&size=20&unreadOnly=").append(unreadOnly);
        if (type != null && !type.isEmpty()) path.append("&type=").append(encode(type));
        if (keyword != null && !keyword.trim().isEmpty()) {
            path.append("&keyword=").append(encode(keyword.trim()));
        }
        return http.get(path.toString());
    }

    public JSONObject detail(String id) throws Exception { return http.get("/publications/" + id); }
    public JSONObject markRead(String id) throws Exception {
        return http.post("/publications/" + id + "/read", new JSONObject(), true);
    }
    public byte[] attachment(String id) throws Exception {
        return http.download("/publications/attachments/" + id);
    }

    private static String encode(String value) {
        try { return java.net.URLEncoder.encode(value, "UTF-8"); }
        catch (Exception ignored) { return value; }
    }
}
