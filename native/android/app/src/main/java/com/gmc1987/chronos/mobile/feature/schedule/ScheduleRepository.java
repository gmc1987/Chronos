package com.gmc1987.chronos.mobile.feature.schedule;

import com.gmc1987.chronos.mobile.core.network.HttpClient;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import org.json.JSONObject;

public final class ScheduleRepository {
    private final HttpClient http;

    public ScheduleRepository(HttpClient http) {
        this.http = http;
    }

    public JSONObject calendar(String startDate, String endDate) throws Exception {
        return http.get("/portal/education/schedule/calendar?startDate=" + encode(startDate)
                + "&endDate=" + encode(endDate));
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
