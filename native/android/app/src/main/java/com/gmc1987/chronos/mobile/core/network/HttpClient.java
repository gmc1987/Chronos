package com.gmc1987.chronos.mobile.core.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public final class HttpClient {
    private final String baseUrl;
    private final SessionStore session;

    public HttpClient(String baseUrl, SessionStore session) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.session = session;
    }

    public JSONObject get(String path) throws Exception {
        return request(path, "GET", null, true, true);
    }

    public JSONObject post(String path, JSONObject body, boolean authenticated) throws Exception {
        return request(path, "POST", body, authenticated, authenticated);
    }

    public JSONObject put(String path, JSONObject body) throws Exception {
        return request(path, "PUT", body, true, true);
    }

    public JSONObject delete(String path) throws Exception {
        return request(path, "DELETE", null, true, true);
    }

    private JSONObject request(
            String path, String method, JSONObject body, boolean authenticated, boolean retry)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(20_000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            if (authenticated && session.hasSession()) {
                connection.setRequestProperty("Authorization", "Bearer " + session.accessToken());
            }
            if (body != null) {
                connection.setDoOutput(true);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            String content = read(status < 400 ? connection.getInputStream() : connection.getErrorStream());
            if (status == 401 && authenticated && retry && session.refreshToken() != null) {
                refresh();
                return request(path, method, body, true, false);
            }
            JSONObject envelope = parse(content, status);
            String code = envelope.optString("code");
            if (status < 200 || status >= 300 || (!"200".equals(code) && !"201".equals(code))) {
                String fallback = status == 403 ? "当前账号无权访问" : "请求失败（" + status + "）";
                throw new ApiException(status, envelope.optString("msg", fallback));
            }
            return envelope;
        } finally {
            connection.disconnect();
        }
    }

    private void refresh() throws Exception {
        JSONObject response = request("/auth/refresh", "POST",
                new JSONObject().put("refreshToken", session.refreshToken()), false, false);
        String access = token(response, "accessToken");
        if (access == null) {
            session.clear();
            throw new ApiException(401, "会话已过期，请重新登录");
        }
        session.update(access, token(response, "refreshToken"));
    }

    public static String token(JSONObject envelope, String key) {
        JSONObject data = envelope.optJSONObject("data");
        String value = data == null ? envelope.optString(key, "") : data.optString(key, "");
        return value.isEmpty() ? null : value;
    }

    private static JSONObject parse(String content, int status) throws ApiException {
        try {
            return new JSONObject(content);
        } catch (Exception error) {
            throw new ApiException(status, "服务端返回了无法识别的数据");
        }
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (InputStream input = stream; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) bytes.write(buffer, 0, length);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
