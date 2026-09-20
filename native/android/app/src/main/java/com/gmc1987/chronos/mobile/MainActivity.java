package com.gmc1987.chronos.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private final ApiClient api = new ApiClient(BuildConfig.API_BASE_URL);
    private LinearLayout root;
    private EditText username, password;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        showLogin();
    }

    private TextView text(String value) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(16); view.setPadding(24, 20, 24, 20); return view;
    }

    private void showLogin() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32, 80, 32, 20);
        root.addView(text("CHRONOS\n统一门户"));
        username = new EditText(this); username.setHint("账号"); root.addView(username);
        password = new EditText(this); password.setHint("密码"); password.setInputType(0x81); root.addView(password);
        Button login = new Button(this); login.setText("登录"); root.addView(login);
        TextView status = text(""); root.addView(status);
        login.setOnClickListener(v -> new Thread(() -> {
            try { api.login(username.getText().toString(), password.getText().toString()); runOnUiThread(this::showPortal); }
            catch (Exception e) { runOnUiThread(() -> status.setText(e.getMessage())); }
        }).start());
        setContentView(root);
    }

    private void showPortal() {
        root.removeAllViews(); root.addView(text("Chronos 门户"));
        addFeature("成绩", "/portal/education/grades");
        addFeature("课表", "/portal/education/schedule");
        addFeature("通知", "/portal/education/class-notices");
        addFeature("家校", "/portal/education/family/notices");
        Button logout = new Button(this); logout.setText("退出登录"); logout.setOnClickListener(v -> { api.clear(); showLogin(); }); root.addView(logout);
    }

    private void addFeature(String title, String path) {
        Button button = new Button(this); button.setText(title); root.addView(button);
        button.setOnClickListener(v -> new Thread(() -> {
            try { String result = api.get(path); runOnUiThread(() -> Toast.makeText(this, result, Toast.LENGTH_LONG).show()); }
            catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show()); }
        }).start());
    }

    static final class ApiClient {
        private final String base; private String access; private String refresh;
        ApiClient(String base) { this.base = base.replaceAll("/$", ""); }
        void clear() { access = null; refresh = null; }
        void login(String user, String pass) throws Exception {
            String body = "{\"username\":\"" + escape(user) + "\",\"password\":\"" + escape(pass) + "\"}";
            String response = request("/consumer/users/login", "POST", body, false);
            access = jsonString(response, "accessToken"); refresh = jsonString(response, "refreshToken");
            if (access == null) throw new IOException("登录失败");
        }
        String get(String path) throws Exception { return request(path, "GET", null, true); }
        private String request(String path, String method, String body, boolean auth) throws Exception {
            HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
            connection.setRequestMethod(method); connection.setRequestProperty("Content-Type", "application/json");
            if (auth && access != null) connection.setRequestProperty("Authorization", "Bearer " + access);
            if (body != null) { connection.setDoOutput(true); try (OutputStream out = connection.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); } }
            int code = connection.getResponseCode(); InputStream stream = code < 400 ? connection.getInputStream() : connection.getErrorStream();
            String response = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            if (code == 401 && auth && refresh != null) { request("/auth/refresh", "POST", "{\"refreshToken\":\"" + escape(refresh) + "\"}", false); return request(path, method, body, false); }
            if (code < 200 || code >= 300) throw new IOException("请求失败（" + code + "）");
            return response;
        }
        private static String jsonString(String json, String key) {
            String marker = "\"" + key + "\""; int start = json.indexOf(marker); if (start < 0) return null;
            start = json.indexOf(':', start) + 1; int quote = json.indexOf('"', start); int end = json.indexOf('"', quote + 1);
            return quote < 0 || end < 0 ? null : json.substring(quote + 1, end);
        }
        private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    }
}
