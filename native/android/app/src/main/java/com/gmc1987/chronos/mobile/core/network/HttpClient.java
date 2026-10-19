package com.gmc1987.chronos.mobile.core.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public final class HttpClient {
    private final String baseUrl;
    private final SessionStore session;
    private final File cacheDirectory;
    private volatile boolean servedFromCache;

    public HttpClient(String baseUrl, SessionStore session, File cacheRoot) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.session = session;
        this.cacheDirectory = new File(cacheRoot, "chronos-offline");
    }

    public JSONObject get(String path) throws Exception {
        try {
            JSONObject response = request(path, "GET", null, true, true);
            servedFromCache = false;
            writeCache(path, response);
            return response;
        } catch (ApiException apiError) {
            // Authorization and business failures must never reveal stale data from an older session.
            throw apiError;
        } catch (IOException networkError) {
            JSONObject cached = readCache(path);
            if (cached == null) throw networkError;
            servedFromCache = true;
            return cached;
        }
    }

    public boolean lastResponseWasCached() { return servedFromCache; }

    public void clearCache() { deleteRecursively(cacheDirectory); }

    public JSONObject post(String path, JSONObject body, boolean authenticated) throws Exception {
        return request(path, "POST", body, authenticated, authenticated);
    }

    public JSONObject put(String path, JSONObject body) throws Exception {
        return request(path, "PUT", body, true, true);
    }

    public JSONObject delete(String path) throws Exception {
        return request(path, "DELETE", null, true, true);
    }

    public JSONObject upload(String path, String fileName, String mimeType, byte[] data)
            throws Exception {
        return upload(path, fileName, mimeType, data, true);
    }

    public byte[] download(String path) throws Exception {
        return download(path, true);
    }

    private JSONObject upload(String path, String fileName, String mimeType, byte[] data,
            boolean retry) throws Exception {
        String boundary = "Chronos-" + java.util.UUID.randomUUID();
        HttpURLConnection connection = open(path, "POST", true);
        try {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                output.write(("Content-Disposition: form-data; name=\"file\"; filename=\""
                        + safeFileName(fileName) + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                output.write(("Content-Type: " + mimeType + "\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                output.write(data);
                output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            String content = read(status < 400 ? connection.getInputStream() : connection.getErrorStream());
            if (status == 401 && retry && session.refreshToken() != null) {
                refresh();
                return upload(path, fileName, mimeType, data, false);
            }
            return validatedEnvelope(content, status);
        } finally {
            connection.disconnect();
        }
    }

    private byte[] download(String path, boolean retry) throws Exception {
        HttpURLConnection connection = open(path, "GET", true);
        try {
            int status = connection.getResponseCode();
            if (status == 401 && retry && session.refreshToken() != null) {
                refresh();
                return download(path, false);
            }
            if (status < 200 || status >= 300) {
                String content = read(connection.getErrorStream());
                throw new ApiException(status, parse(content, status).optString(
                        "msg", "下载失败（" + status + "）"));
            }
            return readBytes(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private JSONObject request(
            String path, String method, JSONObject body, boolean authenticated, boolean retry)
            throws Exception {
        HttpURLConnection connection = open(path, method, authenticated);
        try {
            connection.setRequestProperty("Content-Type", "application/json");
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
            return validatedEnvelope(content, status);
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(String path, String method, boolean authenticated)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(30_000);
        connection.setRequestProperty("Accept", "application/json");
        if (authenticated && session.hasSession()) {
            connection.setRequestProperty("Authorization", "Bearer " + session.accessToken());
        }
        return connection;
    }

    private static JSONObject validatedEnvelope(String content, int status) throws ApiException {
        JSONObject envelope = parse(content, status);
        String code = envelope.optString("code");
        if (status < 200 || status >= 300 || (!"200".equals(code) && !"201".equals(code))) {
            String fallback = status == 403 ? "当前账号无权访问" : "请求失败（" + status + "）";
            throw new ApiException(status, envelope.optString("msg", fallback));
        }
        return envelope;
    }

    private static String safeFileName(String value) {
        return value == null ? "attachment" : value.replace("\"", "_").replace("\r", "_")
                .replace("\n", "_");
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

    private static byte[] readBytes(InputStream stream) throws IOException {
        if (stream == null) return new byte[0];
        try (InputStream input = stream; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) bytes.write(buffer, 0, length);
            return bytes.toByteArray();
        }
    }

    private void writeCache(String path, JSONObject response) {
        if (!cacheable(path)) return;
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) return;
        File target = new File(cacheDirectory, Integer.toHexString(path.hashCode()) + ".json");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(target)) {
            output.write(response.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) { }
    }

    private JSONObject readCache(String path) {
        if (!cacheable(path)) return null;
        File target = new File(cacheDirectory, Integer.toHexString(path.hashCode()) + ".json");
        if (!target.isFile() || System.currentTimeMillis() - target.lastModified() > 86_400_000L) return null;
        try (java.io.FileInputStream input = new java.io.FileInputStream(target)) {
            return new JSONObject(read(input));
        } catch (Exception ignored) { return null; }
    }

    private boolean cacheable(String path) {
        return path.startsWith("/portal/bootstrap")
                || path.startsWith("/publications")
                || path.startsWith("/portal/education/family/")
                || path.startsWith("/education/portal/");
    }

    private static void deleteRecursively(File value) {
        File[] children = value.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        if (value.exists()) value.delete();
    }
}
