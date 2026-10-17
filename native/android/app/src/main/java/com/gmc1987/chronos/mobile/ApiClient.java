package com.gmc1987.chronos.mobile;

import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

final class ApiClient {
    private final String base;
    private final SharedPreferences preferences;
    private final SecureTokenStore tokenStore;
    private String accessToken;
    private String refreshToken;

    ApiClient(String base, SharedPreferences preferences) {
        this.base = base.replaceAll("/$", "");
        this.preferences = preferences;
        this.tokenStore = new SecureTokenStore(preferences);
        if (preferences.getBoolean("remember", false)) {
            accessToken = tokenStore.get("access_token");
            refreshToken = tokenStore.get("refresh_token");
        }
    }

    boolean hasSession() {
        return accessToken != null && !accessToken.isEmpty();
    }

    JSONObject login(String username, String password, boolean remember) throws Exception {
        JSONObject body = new JSONObject().put("username", username).put("password", password);
        JSONObject response = request("/auth/login", "POST", body, false, false);
        accessToken = token(response, "accessToken");
        refreshToken = token(response, "refreshToken");
        if (!hasSession()) throw new IOException("服务端未返回访问令牌");
        preferences.edit().putBoolean("remember", remember).apply();
        if (remember) {
            tokenStore.put("access_token", accessToken);
            tokenStore.put("refresh_token", refreshToken);
        } else {
            tokenStore.clear();
        }
        return response;
    }

    JSONObject get(String path) throws Exception {
        return request(path, "GET", null, true, true);
    }

    void revoke() {
        try {
            if (refreshToken != null) {
                request("/auth/revoke", "POST",
                        new JSONObject().put("refreshToken", refreshToken), false, false);
            }
        } catch (Exception ignored) {
            // Local credentials must still be removed when the revoke endpoint is unreachable.
        } finally {
            clear();
        }
    }

    void clear() {
        accessToken = null;
        refreshToken = null;
        tokenStore.clear();
        preferences.edit().remove("remember").apply();
    }

    private JSONObject request(
            String path,
            String method,
            JSONObject body,
            boolean authenticated,
            boolean retry) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(20_000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            if (authenticated && hasSession()) {
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            }
            if (body != null) {
                connection.setDoOutput(true);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            String content = read(status < 400 ? connection.getInputStream() : connection.getErrorStream());
            if (status == 401 && authenticated && retry && refreshToken != null) {
                refresh();
                return request(path, method, body, true, false);
            }
            JSONObject envelope;
            try {
                envelope = new JSONObject(content);
            } catch (Exception exception) {
                throw new ApiException(status, "服务端返回了无法识别的数据");
            }
            String businessCode = envelope.optString("code");
            if (status < 200 || status >= 300
                    || (!"200".equals(businessCode) && !"201".equals(businessCode))) {
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
                new JSONObject().put("refreshToken", refreshToken), false, false);
        String nextAccess = token(response, "accessToken");
        String nextRefresh = token(response, "refreshToken");
        if (nextAccess == null) {
            clear();
            throw new ApiException(401, "会话已过期，请重新登录");
        }
        accessToken = nextAccess;
        if (nextRefresh != null) refreshToken = nextRefresh;
        if (preferences.getBoolean("remember", false)) {
            tokenStore.put("access_token", accessToken);
            tokenStore.put("refresh_token", refreshToken);
        }
    }

    private static String token(JSONObject envelope, String key) {
        JSONObject data = envelope.optJSONObject("data");
        String value = data == null ? envelope.optString(key, "") : data.optString(key, "");
        return value.isEmpty() ? null : value;
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

    static final class ApiException extends IOException {
        final int status;

        ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
