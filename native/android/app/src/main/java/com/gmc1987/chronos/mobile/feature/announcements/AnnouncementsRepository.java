package com.gmc1987.chronos.mobile.feature.announcements;

import com.gmc1987.chronos.mobile.core.network.HttpClient;
import org.json.JSONObject;

public final class AnnouncementsRepository {
    private final HttpClient http;
    public AnnouncementsRepository(HttpClient http) { this.http = http; }
    public JSONObject list() throws Exception { return http.get("/publications"); }
}
