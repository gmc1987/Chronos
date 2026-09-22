package com.gmc1987.chronos.mobile;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Small native shell around the existing portal APIs. The server remains the source of truth for roles. */
public final class MainActivity extends Activity {
    private static final String PREFS = "chronos_session";
    private ApiClient api;
    private LinearLayout root;
    private TextView status;
    private String role = "student";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        api = new ApiClient(BuildConfig.API_BASE_URL, getSharedPreferences(PREFS, MODE_PRIVATE));
        role = getSharedPreferences(PREFS, MODE_PRIVATE).getString("role", "student");
        if (api.hasAccess()) showHome(); else showLogin();
    }

    private TextView text(String value) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(16); view.setTextColor(Color.DKGRAY);
        view.setPadding(24, 16, 24, 16); return view;
    }

    private void base(String title) {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 40, 24, 24); root.addView(text(title));
        setContentView(new ScrollView(this) {{ addView(root); }});
    }

    private void showLogin() {
        base("CHRONOS\n统一教育门户");
        EditText username = new EditText(this); username.setHint("账号");
        EditText password = new EditText(this); password.setHint("密码");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(username); root.addView(password);
        CheckBox remember = new CheckBox(this); remember.setText("保持登录"); remember.setChecked(true);
        root.addView(remember);
        Button login = new Button(this); login.setText("登录"); root.addView(login);
        status = text(""); root.addView(status);
        login.setOnClickListener(v -> {
            if (username.getText().length() == 0 || password.getText().length() == 0) {
                status.setText("请输入账号和密码"); return;
            }
            setBusy(login, "正在登录…");
            new Thread(() -> {
                try {
                    String response = api.login(username.getText().toString(), password.getText().toString(), remember.isChecked());
                    role = detectRole(response);
                    api.saveRole(role);
                    runOnUiThread(this::showHome);
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        showError(status, e);
                        Button retry = new Button(this);
                        retry.setText("重试");
                        retry.setOnClickListener(v -> loadPage(title, path));
                        root.addView(retry);
                    });
                }
                finally { runOnUiThread(() -> setBusy(login, "登录")); }
            }).start();
        });
    }

    private void showHome() {
        base("Chronos 教育门户 · " + roleLabel(role));
        status = text("正在加载角色入口…"); root.addView(status);
        new Thread(() -> {
            try {
                String bootstrap = api.get("/portal/bootstrap");
                String serverRole = detectRole(bootstrap);
                if (!serverRole.equals("unknown")) { role = serverRole; api.saveRole(role); }
                runOnUiThread(() -> renderHome());
            } catch (Exception e) { runOnUiThread(() -> { renderHome(); showError(status, e); }); }
        }).start();
    }

    private void renderHome() {
        root.removeAllViews(); root.addView(text("Chronos 教育门户 · " + roleLabel(role)));
        status = text("请选择服务"); root.addView(status);
        if (role.equals("teacher")) {
            addPage("我的班级", "/portal/education/head-teacher/classes");
            addPage("会议", "/portal/education/meetings");
            addPage("监考安排", "/portal/education/exam/my-invigilations");
        } else if (role.equals("parent")) {
            addPage("孩子信息", "/portal/education/family/children");
            addPage("家校通知", "/portal/education/family/notices");
            addPage("班级通知", "/portal/education/class-notices");
        } else {
            addPage("我的课表", "/portal/education/schedule");
            addPage("我的成绩", "/portal/education/grades");
            addPage("我的考试", "/portal/education/exam/my-exams");
            addPage("班级通知", "/portal/education/class-notices");
        }
        Space gap = new Space(this); root.addView(gap, new LinearLayout.LayoutParams(1, 24));
        Button logout = new Button(this); logout.setText("退出登录");
        logout.setOnClickListener(v -> { api.clear(); role = "student"; showLogin(); });
        root.addView(logout);
    }

    private void addPage(String title, String path) {
        Button button = new Button(this); button.setText(title); root.addView(button);
        button.setOnClickListener(v -> loadPage(title, path));
    }

    private void loadPage(String title, String path) {
        base(title); status = text("加载中…"); root.addView(status);
        Button back = new Button(this); back.setText("返回门户"); root.addView(back);
        back.setOnClickListener(v -> showHome());
        new Thread(() -> {
            try {
                String response = api.get(path);
                runOnUiThread(() -> showData(response));
            } catch (Exception e) { runOnUiThread(() -> showError(status, e)); }
        }).start();
    }

    private void showData(String response) {
        String data = unwrap(response);
        if (data.length() == 0 || data.equals("null") || data.equals("{}") || data.equals("[]")) {
            status.setText("暂无数据"); return;
        }
        status.setText(formatJson(data));
    }

    private void showError(TextView target, Exception error) {
        target.setText("加载失败：" + (error.getMessage() == null ? "请稍后重试" : error.getMessage()));
    }

    private void setBusy(Button button, String label) {
        button.setText(label); button.setEnabled(!label.contains("…"));
    }

    private static String roleLabel(String value) {
        if ("teacher".equals(value)) return "教师";
        if ("parent".equals(value)) return "家长";
        return "学生";
    }

    private static String detectRole(String json) {
        String value = json == null ? "" : json.toLowerCase(Locale.ROOT);
        if (value.contains("parent") || value.contains("guardian") || value.contains("家长")) return "parent";
        if (value.contains("teacher") || value.contains("head_teacher") || value.contains("教师")) return "teacher";
        if (value.contains("student") || value.contains("学生")) return "student";
        return "unknown";
    }

    private static String unwrap(String json) {
        String data = jsonString(json, "data");
        return data == null ? json : data;
    }

    private static String formatJson(String json) {
        return json.replace("{", "{\n").replace("}", "\n}").replace(",", ",\n");
    }

    private static String jsonString(String json, String key) {
        String marker = "\"" + key + "\""; int start = json.indexOf(marker);
        if (start < 0) return null; int colon = json.indexOf(':', start);
        int quote = json.indexOf('"', colon + 1);
        if (colon < 0 || quote < 0) return null;
        int end = json.indexOf('"', quote + 1);
        return end < 0 ? null : json.substring(quote + 1, end);
    }

    static final class ApiClient {
        private final String base; private final SharedPreferences prefs;
        private String access; private String refresh;
        ApiClient(String base, SharedPreferences prefs) {
            this.base = base.replaceAll("/$", ""); this.prefs = prefs;
            if (prefs.getBoolean("remember", false)) {
                access = prefs.getString("access_token", null); refresh = prefs.getString("refresh_token", null);
            }
        }
        boolean hasAccess() { return access != null && access.length() > 0; }
        void saveRole(String value) { prefs.edit().putString("role", value).apply(); }
        void clear() { access = null; refresh = null; prefs.edit().clear().apply(); }
        String login(String user, String pass, boolean remember) throws Exception {
            String body = "{\"username\":\"" + escape(user) + "\",\"password\":\"" + escape(pass) + "\"}";
            String response = request("/consumer/users/login", "POST", body, false);
            access = jsonString(response, "accessToken"); refresh = jsonString(response, "refreshToken");
            if (!hasAccess()) throw new IOException("登录失败");
            SharedPreferences.Editor edit = prefs.edit().putBoolean("remember", remember);
            if (remember) {
                edit.putString("access_token", access);
                if (refresh != null) edit.putString("refresh_token", refresh);
            } else {
                edit.remove("access_token").remove("refresh_token");
            }
            edit.apply();
            return response;
        }
        String get(String path) throws Exception { return request(path, "GET", null, true); }
        private String request(String path, String method, String body, boolean auth) throws Exception {
            HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
            connection.setRequestMethod(method); connection.setConnectTimeout(12000); connection.setReadTimeout(20000);
            connection.setRequestProperty("Content-Type", "application/json");
            if (auth && hasAccess()) connection.setRequestProperty("Authorization", "Bearer " + access);
            if (body != null) { connection.setDoOutput(true); try (OutputStream out = connection.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); } }
            int code = connection.getResponseCode(); InputStream stream = code < 400 ? connection.getInputStream() : connection.getErrorStream();
            String response = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            if (code == 401 && auth && refresh != null) {
                String refreshed = request("/auth/refresh", "POST", "{\"refreshToken\":\"" + escape(refresh) + "\"}", false);
                access = jsonString(refreshed, "accessToken");
                if (hasAccess()) {
                    if (prefs.getBoolean("remember", false)) prefs.edit().putString("access_token", access).apply();
                    return request(path, method, body, true);
                }
            }
            if (code < 200 || code >= 300) throw new IOException("请求失败（" + code + "）");
            return response;
        }
        private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    }
}
