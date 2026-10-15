package com.gmc1987.chronos.mobile;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final int BRAND = Color.rgb(8, 123, 104);
    private static final int BRAND_DARK = Color.rgb(6, 61, 57);
    private static final int BACKGROUND = Color.rgb(244, 247, 248);
    private static final int TEXT = Color.rgb(23, 32, 42);
    private static final int MUTED = Color.rgb(113, 128, 138);
    private static final String PREFS = "chronos_session";
    private final SimpleDateFormat isoDate = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
    private ApiClient api;
    private SharedPreferences preferences;
    private PortalState portal;
    private FrameLayout content;
    private LinearLayout tabBar;
    private Calendar weekStart;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        api = new ApiClient(BuildConfig.API_BASE_URL, preferences);
        if (api.hasSession()) initializePortal(); else showLogin();
    }

    private void showLogin() {
        LinearLayout page = vertical(24);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.addView(space(72));
        TextView brand = label("CHRONOS", 28, BRAND_DARK, true);
        page.addView(brand);
        page.addView(label("统一教育门户", 18, TEXT, true));
        page.addView(label("连接学校服务，查看课程与协同事项", 14, MUTED, false));
        page.addView(space(28));
        LinearLayout card = card();
        EditText username = input("账号", false);
        EditText password = input("密码", true);
        CheckBox remember = new CheckBox(this);
        remember.setText("保持登录");
        remember.setChecked(true);
        Button submit = primaryButton("登录");
        TextView state = label("", 14, Color.rgb(190, 62, 46), false);
        card.addView(username);
        card.addView(password);
        card.addView(remember);
        card.addView(submit);
        card.addView(state);
        page.addView(card);
        setContentView(scroll(page));
        submit.setOnClickListener(view -> {
            String user = username.getText().toString().trim();
            String pass = password.getText().toString();
            if (user.isEmpty() || pass.isEmpty()) {
                state.setText("请输入账号和密码");
                return;
            }
            setBusy(submit, true, "正在登录…");
            state.setText("");
            run(() -> api.login(user, pass, remember.isChecked()), ignored -> initializePortal(),
                    error -> {
                        state.setText(message(error, "登录失败，请检查账号、密码和网络"));
                        setBusy(submit, false, "登录");
                    });
        });
    }

    private void initializePortal() {
        LinearLayout page = centeredState("正在初始化工作台…", true);
        setContentView(page);
        run(() -> api.get("/portal/bootstrap"), response -> {
            try {
                portal = PortalState.from(response);
                showShell();
            } catch (Exception error) {
                showInitializationError(error);
            }
        }, this::showInitializationError);
    }

    private void showInitializationError(Exception error) {
        LinearLayout page = centeredState(message(error, "工作台初始化失败"), false);
        Button retry = primaryButton("重新加载");
        Button logout = secondaryButton("退出登录");
        retry.setOnClickListener(view -> initializePortal());
        logout.setOnClickListener(view -> {
            api.clear();
            showLogin();
        });
        page.addView(retry);
        page.addView(logout);
        setContentView(page);
    }

    private void showShell() {
        LinearLayout shell = vertical(0);
        shell.setBackgroundColor(BACKGROUND);
        content = new FrameLayout(this);
        shell.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(dp(6), dp(7), dp(6), dp(7));
        tabBar.setBackgroundColor(Color.WHITE);
        shell.addView(tabBar);
        setContentView(shell);
        renderTabs();
        showDashboard();
    }

    private void renderTabs() {
        tabBar.removeAllViews();
        addTab("工作台", this::showDashboard);
        if (portal.workspace == PortalState.Workspace.STUDENT) {
            addTab("成绩", () -> showRemoteList(
                    "成绩", "/portal/education/grades", "当前学期暂无已发布成绩", this::gradeRows));
            addTab("考试", () -> showRemoteList(
                    "考试", "/portal/education/exam/my-exams", "近期没有考试安排", this::examRows));
            addTab("通知", () -> showRemoteList(
                    "通知", "/publications", "当前没有可见通知", this::publicationRows));
        } else {
            addTab("应用", this::showApplications);
            addTab("公告", () -> showRemoteList(
                    "公告", "/publications", "当前没有可见公告", this::publicationRows));
        }
        addTab("我的", this::showProfile);
    }

    private void addTab(String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(12);
        button.setTextColor(BRAND_DARK);
        button.setAllCaps(false);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setMinHeight(dp(48));
        button.setOnClickListener(view -> action.run());
        tabBar.addView(button, new LinearLayout.LayoutParams(0, dp(52), 1));
    }

    private void showDashboard() {
        LinearLayout page = page(portal.workspace == PortalState.Workspace.STUDENT
                ? "学生工作台" : portal.workspace == PortalState.Workspace.TEACHER
                ? "教师工作台" : portal.workspace == PortalState.Workspace.MANAGEMENT
                ? "管理工作台" : "职工工作台");
        page.addView(summaryCard());
        if (portal.workspace == PortalState.Workspace.STUDENT
                || portal.workspace == PortalState.Workspace.TEACHER) {
            weekStart = startOfWeek(new Date());
            page.addView(scheduleCard());
        }
        if (portal.workspace == PortalState.Workspace.STUDENT) {
            addLearningCards(page);
        } else {
            addStaffCards(page);
        }
        setPage(page);
    }

    private View summaryCard() {
        LinearLayout card = card();
        card.addView(label(greeting() + "，" + portal.displayName(), 22, TEXT, true));
        String organization = portal.organization();
        if (!organization.isEmpty()) card.addView(label(organization, 14, MUTED, false));
        if (portal.workspace == PortalState.Workspace.STUDENT) {
            card.addView(label("班级信息待服务端学生摘要补充", 13, MUTED, false));
        }
        card.addView(label(portal.termName() + " · " + dateLabel(new Date()), 13, MUTED, false));
        return card;
    }

    private View scheduleCard() {
        LinearLayout card = card();
        LinearLayout heading = horizontal();
        TextView title = label("本周课表", 18, TEXT, true);
        Button toggle = secondaryButton(preferences.getBoolean(scheduleKey(), true) ? "收起" : "展开");
        heading.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        heading.addView(toggle);
        card.addView(heading);
        TextView range = label(weekRange(), 13, MUTED, false);
        card.addView(range);
        LinearLayout body = vertical(8);
        card.addView(body);
        boolean expanded = preferences.getBoolean(scheduleKey(), true);
        toggle.setOnClickListener(view -> {
            boolean next = !preferences.getBoolean(scheduleKey(), true);
            preferences.edit().putBoolean(scheduleKey(), next).apply();
            showDashboard();
        });
        loadSchedule(body, expanded);
        return card;
    }

    private void loadSchedule(LinearLayout body, boolean expanded) {
        body.removeAllViews();
        ProgressBar progress = new ProgressBar(this);
        body.addView(progress);
        String path = "/portal/education/schedule/calendar?startDate=" + encode(isoDate.format(weekStart.getTime()))
                + "&endDate=" + encode(isoDate.format(endOfWeek().getTime()));
        run(() -> api.get(path), response -> {
            body.removeAllViews();
            JSONObject data = response.optJSONObject("data");
            JSONArray occurrences = data == null ? null : data.optJSONArray("occurrences");
            if (!expanded) {
                body.addView(collapsedSchedule(occurrences));
            } else {
                LinearLayout controls = horizontal();
                Button previous = secondaryButton("上一周");
                Button current = secondaryButton("本周");
                Button next = secondaryButton("下一周");
                controls.addView(previous, new LinearLayout.LayoutParams(0, dp(48), 1));
                controls.addView(current, new LinearLayout.LayoutParams(0, dp(48), 1));
                controls.addView(next, new LinearLayout.LayoutParams(0, dp(48), 1));
                body.addView(controls);
                previous.setOnClickListener(view -> moveWeek(-7));
                current.setOnClickListener(view -> {
                    weekStart = startOfWeek(new Date());
                    showDashboard();
                });
                next.setOnClickListener(view -> moveWeek(7));
                if (occurrences == null || occurrences.length() == 0) {
                    body.addView(empty("本周暂无课程"));
                } else {
                    body.addView(scheduleGrid(occurrences));
                }
            }
        }, error -> {
            body.removeAllViews();
            body.addView(errorState(message(error, "个人课表加载失败"), () -> loadSchedule(body, expanded)));
        });
    }

    private View collapsedSchedule(JSONArray occurrences) {
        String today = isoDate.format(new Date());
        List<JSONObject> todayItems = occurrencesForDate(occurrences, today);
        LinearLayout summary = vertical(5);
        summary.addView(label(todayItems.isEmpty()
                ? "今日无课程" : "今日 " + todayItems.size() + " 节课", 15, TEXT, true));
        if (!todayItems.isEmpty()) {
            JSONObject next = todayItems.get(0);
            JSONObject entry = next.optJSONObject("entry");
            summary.addView(label("下一节：" + (entry == null ? "课程" : entry.optString("courseName", "课程"))
                    + " · " + timeOf(next), 13, MUTED, false));
        }
        return summary;
    }

    private View scheduleGrid(JSONArray occurrences) {
        List<Integer> periods = new ArrayList<>();
        for (int i = 0; i < occurrences.length(); i++) {
            int period = occurrences.optJSONObject(i).optInt("effectivePeriodNo", 0);
            if (period > 0 && !periods.contains(period)) periods.add(period);
        }
        Collections.sort(periods);
        LinearLayout table = vertical(1);
        table.setBackgroundColor(Color.rgb(225, 232, 233));
        LinearLayout header = horizontal();
        header.addView(gridCell("节次", true, dp(76)));
        Calendar day = (Calendar) weekStart.clone();
        for (int column = 0; column < 7; column++) {
            header.addView(gridCell("周" + "一二三四五六日".charAt(column)
                    + "\n" + new SimpleDateFormat("MM-dd", Locale.ROOT).format(day.getTime()), true, dp(120)));
            day.add(Calendar.DATE, 1);
        }
        table.addView(header);
        for (int period : periods) {
            LinearLayout row = horizontal();
            row.addView(gridCell("第 " + period + " 节", true, dp(76)));
            Calendar date = (Calendar) weekStart.clone();
            for (int column = 0; column < 7; column++) {
                JSONObject lesson = lessonAt(occurrences, isoDate.format(date.getTime()), period);
                row.addView(gridCell(lesson == null ? "—" : lessonText(lesson), false, dp(120)));
                date.add(Calendar.DATE, 1);
            }
            table.addView(row);
        }
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setFillViewport(false);
        horizontal.addView(table);
        return horizontal;
    }

    private void addLearningCards(LinearLayout page) {
        addCapability(page, "课件", "查看课程课件与最近更新", "课件", "courseware", "material");
        addCapability(page, "错题沉淀", "回顾错题与待掌握内容", "错题", "error-book", "wrong");
        addCapability(page, "题库", "进入授权练习与题库", "题库", "question", "exercise");
    }

    private void addCapability(LinearLayout page, String title, String description, String... keys) {
        JSONObject application = findApplication(keys);
        LinearLayout card = card();
        card.addView(label(title, 17, TEXT, true));
        card.addView(label(application == null ? "建设中 · 当前服务端未开放移动入口" : description,
                13, application == null ? MUTED : BRAND, false));
        if (application != null) {
            Button open = secondaryButton("在应用中心查看");
            open.setOnClickListener(view -> showApplications());
            card.addView(open);
        }
        page.addView(card);
    }

    private void addStaffCards(LinearLayout page) {
        addContributionCard(page, "我的待办", "WORKFLOW", "当前没有待处理事项");
        addRemoteSummaryCard(page, "近期会议", "/portal/education/meetings",
                "未来暂无会议", this::meetingRows);
        if (portal.workspace == PortalState.Workspace.MANAGEMENT) {
            addRemoteSummaryCard(page, "督办事项", "/portal/education/supervision/tasks",
                    "当前没有督办事项", this::genericRows);
            addMetricsCard(page);
        } else {
            addApplicationGroupCard(page, "协同办公", "collaboration", "协同", "office", "leave", "trip");
            if (portal.workspace == PortalState.Workspace.TEACHER) {
                addApplicationGroupCard(page, "教学中心", "education", "教学", "题库", "课件");
            } else {
                addContributionCard(page, "公告摘要", "ANNOUNCEMENT", "当前没有可见公告");
            }
        }
    }

    private void addContributionCard(LinearLayout page, String title, String code, String emptyText) {
        JSONObject contribution = portal.contribution(code);
        LinearLayout card = card();
        card.addView(label(title, 17, TEXT, true));
        if (contribution != null && contribution.optBoolean("available", false)) {
            JSONObject data = contribution.optJSONObject("data");
            card.addView(label(data == null || data.length() == 0 ? emptyText : "服务已接入，可在应用中心处理",
                    13, MUTED, false));
        } else {
            card.addView(label("建设中 · " + (contribution == null
                    ? "服务端尚未提供摘要接口" : contribution.optString("message", "服务暂不可用")),
                    13, MUTED, false));
        }
        page.addView(card);
    }

    private void addRemoteSummaryCard(
            LinearLayout page, String title, String path, String emptyText, RowMapper mapper) {
        LinearLayout card = card();
        card.addView(label(title, 17, TEXT, true));
        LinearLayout body = vertical(4);
        body.addView(new ProgressBar(this));
        card.addView(body);
        page.addView(card);
        run(() -> api.get(path), response -> {
            body.removeAllViews();
            List<String> rows = mapper.rows(response.opt("data"));
            if (rows.isEmpty()) body.addView(empty(emptyText));
            else for (String row : rows.subList(0, Math.min(3, rows.size()))) {
                body.addView(label("• " + row, 13, TEXT, false));
            }
        }, error -> {
            body.removeAllViews();
            body.addView(errorState(message(error, title + "加载失败"),
                    () -> {
                        showDashboard();
                    }));
        });
    }

    private void addApplicationGroupCard(LinearLayout page, String title, String... keys) {
        LinearLayout card = card();
        card.addView(label(title, 17, TEXT, true));
        List<String> names = applicationNames(keys);
        card.addView(label(names.isEmpty() ? "当前账号暂无授权应用" : join(names, " · "),
                13, MUTED, false));
        page.addView(card);
    }

    private void addMetricsCard(LinearLayout page) {
        LinearLayout card = card();
        card.addView(label("数据概览", 17, TEXT, true));
        JSONArray metrics = portal.education.optJSONArray("metrics");
        if (metrics == null || metrics.length() == 0) {
            card.addView(empty("当前没有授权指标"));
        } else {
            for (int i = 0; i < metrics.length(); i++) {
                JSONObject metric = metrics.optJSONObject(i);
                card.addView(label(metric.optString("label", "指标") + "  "
                        + metric.optString("value", "—"), 15, TEXT, false));
            }
        }
        page.addView(card);
    }

    private void showApplications() {
        LinearLayout page = page("应用");
        if (portal.applications.length() == 0) {
            page.addView(empty("当前账号暂无授权应用"));
        }
        for (int i = 0; i < portal.applications.length(); i++) {
            JSONObject application = portal.applications.optJSONObject(i);
            LinearLayout card = card();
            card.addView(label(application.optString("name", "应用"), 17, TEXT, true));
            card.addView(label(application.optString("description", "已由服务端授权"),
                    13, MUTED, false));
            String route = application.optString("routePath", "");
            if (supportsNativeRoute(route)) {
                Button open = secondaryButton("打开");
                open.setOnClickListener(view -> openNativeRoute(route));
                card.addView(open);
            } else {
                card.addView(label(route.isEmpty() ? "移动入口建设中" : "请前往 Web 端处理 · " + route,
                        12, BRAND, false));
            }
            page.addView(card);
        }
        setPage(page);
    }

    private boolean supportsNativeRoute(String route) {
        return route.contains("/education/schedule")
                || route.contains("/education/grades")
                || route.contains("/education/exam/my-exams")
                || route.contains("/education/meetings")
                || route.contains("/education/supervision")
                || route.contains("/publications");
    }

    private void openNativeRoute(String route) {
        if (route.contains("/education/grades")) {
            showRemoteList("成绩", "/portal/education/grades", "当前学期暂无已发布成绩", this::gradeRows);
        } else if (route.contains("/education/exam/my-exams")) {
            showRemoteList("考试", "/portal/education/exam/my-exams", "近期没有考试安排", this::examRows);
        } else if (route.contains("/education/meetings")) {
            showRemoteList("会议", "/portal/education/meetings", "未来暂无会议", this::meetingRows);
        } else if (route.contains("/education/supervision")) {
            showRemoteList("督办事项", "/portal/education/supervision/tasks", "当前没有督办事项", this::genericRows);
        } else if (route.contains("/publications")) {
            showRemoteList("公告", "/publications", "当前没有可见公告", this::publicationRows);
        } else {
            showDashboard();
        }
    }

    private void showProfile() {
        LinearLayout page = page("我的");
        LinearLayout card = card();
        card.addView(label(portal.displayName(), 22, TEXT, true));
        card.addView(label(workspaceLabel(), 14, BRAND, true));
        card.addView(label("账号：" + portal.user.optString("username", "—"), 13, MUTED, false));
        if (!portal.organization().isEmpty()) {
            card.addView(label("组织：" + portal.organization(), 13, MUTED, false));
        }
        String employeeCode = portal.user.optString("employeeCode", "");
        if (!employeeCode.isEmpty()) card.addView(label("工号：" + employeeCode, 13, MUTED, false));
        page.addView(card);
        LinearLayout settings = card();
        settings.addView(label("消息设置", 16, TEXT, true));
        settings.addView(label("隐私与安全", 16, TEXT, true));
        settings.addView(label("Chronos Android 0.1.0", 13, MUTED, false));
        page.addView(settings);
        Button logout = primaryButton("退出登录");
        logout.setOnClickListener(view -> {
            setBusy(logout, true, "正在退出…");
            new Thread(() -> {
                api.revoke();
                runOnUiThread(this::showLogin);
            }).start();
        });
        page.addView(logout);
        setPage(page);
    }

    private void showRemoteList(String title, String path, String emptyText, RowMapper mapper) {
        LinearLayout page = page(title);
        LinearLayout body = vertical(8);
        body.addView(centeredState("正在加载…", true));
        page.addView(body);
        setPage(page);
        run(() -> api.get(path), response -> {
            body.removeAllViews();
            List<String> rows = mapper.rows(response.opt("data"));
            if (rows.isEmpty()) {
                body.addView(empty(emptyText));
                return;
            }
            for (String row : rows) {
                LinearLayout card = card();
                card.addView(label(row, 15, TEXT, false));
                body.addView(card);
            }
        }, error -> {
            body.removeAllViews();
            body.addView(errorState(message(error, title + "加载失败"),
                    () -> showRemoteList(title, path, emptyText, mapper)));
        });
    }

    private List<String> gradeRows(Object data) {
        List<String> rows = new ArrayList<>();
        for (JSONObject item : objects(data)) {
            rows.add(item.optString("courseName", "课程")
                    + " · " + item.optString("totalScore", "—")
                    + " · " + item.optString("gradeLevel", "未评级"));
        }
        return rows;
    }

    private List<String> examRows(Object data) {
        List<String> rows = new ArrayList<>();
        for (JSONObject item : objects(data)) {
            rows.add(item.optString("subjectName", item.optString("planName", "考试"))
                    + "\n" + item.optString("examDate", "时间待定") + " "
                    + item.optString("startTime", "") + "-" + item.optString("endTime", "")
                    + " · " + item.optString("classroomName", "考场待定")
                    + " · 座位 " + item.optString("seatNo", "—"));
        }
        return rows;
    }

    private List<String> meetingRows(Object data) {
        List<String> rows = new ArrayList<>();
        for (JSONObject item : objects(data)) {
            JSONObject meeting = item.optJSONObject("meeting");
            JSONObject room = item.optJSONObject("room");
            if (meeting == null) meeting = item;
            rows.add(meeting.optString("title", meeting.optString("subject", "会议"))
                    + " · " + meeting.optString("startTime", meeting.optString("startAt", "时间待定"))
                    + " · " + (room == null ? "地点待定" : room.optString("roomName", "地点待定")));
        }
        return rows;
    }

    private List<String> publicationRows(Object data) {
        if (data instanceof JSONObject) {
            JSONObject object = (JSONObject) data;
            Object content = object.opt("content");
            if (content != null) return genericRows(content);
            Object records = object.opt("records");
            if (records != null) return genericRows(records);
        }
        return genericRows(data);
    }

    private List<String> genericRows(Object data) {
        List<String> rows = new ArrayList<>();
        for (JSONObject item : objects(data)) {
            rows.add(first(item, "title", "subject", "name", "taskName", "content"));
        }
        return rows;
    }

    private LinearLayout page(String title) {
        LinearLayout page = vertical(14);
        page.setPadding(dp(16), dp(22), dp(16), dp(24));
        page.addView(label(title, 24, BRAND_DARK, true));
        return page;
    }

    private void setPage(LinearLayout page) {
        content.removeAllViews();
        content.addView(scroll(page));
    }

    private LinearLayout card() {
        LinearLayout card = vertical(8);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(5), 0, dp(5));
        card.setLayoutParams(params);
        card.setElevation(dp(1));
        return card;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.15f);
        view.setPadding(dp(2), dp(5), dp(2), dp(5));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private EditText input(String hint, boolean password) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setMinHeight(dp(52));
        if (password) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        return input;
    }

    private Button primaryButton(String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setBackgroundColor(BRAND);
        button.setMinHeight(dp(48));
        return button;
    }

    private Button secondaryButton(String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextColor(BRAND);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setBackgroundColor(Color.rgb(235, 247, 244));
        button.setMinHeight(dp(44));
        return button;
    }

    private View empty(String text) {
        TextView view = label(text, 14, MUTED, false);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(88));
        return view;
    }

    private View errorState(String text, Runnable retry) {
        LinearLayout state = vertical(6);
        TextView message = label(text, 14, Color.rgb(190, 62, 46), false);
        Button button = secondaryButton("重试");
        button.setOnClickListener(view -> retry.run());
        state.addView(message);
        state.addView(button);
        return state;
    }

    private LinearLayout centeredState(String text, boolean loading) {
        LinearLayout state = vertical(12);
        state.setGravity(Gravity.CENTER);
        state.setPadding(dp(24), dp(48), dp(24), dp(48));
        if (loading) state.addView(new ProgressBar(this));
        TextView label = label(text, 15, loading ? MUTED : Color.rgb(190, 62, 46), false);
        label.setGravity(Gravity.CENTER);
        state.addView(label);
        return state;
    }

    private ScrollView scroll(View child) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.addView(child);
        return scroll;
    }

    private LinearLayout vertical(int gap) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        if (gap > 0) layout.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private View gridCell(String value, boolean heading, int width) {
        TextView cell = label(value, heading ? 12 : 11, heading ? BRAND_DARK : TEXT, heading);
        cell.setGravity(Gravity.CENTER);
        cell.setBackgroundColor(Color.WHITE);
        cell.setMinHeight(dp(76));
        cell.setMaxLines(5);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, dp(1), dp(1));
        cell.setLayoutParams(params);
        return cell;
    }

    private Space space(int height) {
        Space space = new Space(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return space;
    }

    private void moveWeek(int days) {
        weekStart.add(Calendar.DATE, days);
        showDashboard();
    }

    private Calendar startOfWeek(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        calendar.add(Calendar.DATE, day == Calendar.SUNDAY ? -6 : Calendar.MONDAY - day);
        return calendar;
    }

    private Calendar endOfWeek() {
        Calendar end = (Calendar) weekStart.clone();
        end.add(Calendar.DATE, 6);
        return end;
    }

    private String weekRange() {
        return isoDate.format(weekStart.getTime()) + " 至 " + isoDate.format(endOfWeek().getTime());
    }

    private List<JSONObject> occurrencesForDate(JSONArray array, String date) {
        List<JSONObject> result = new ArrayList<>();
        if (array == null) return result;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null && date.equals(item.optString("date"))) result.add(item);
        }
        result.sort(Comparator.comparingInt(value -> value.optInt("effectivePeriodNo", 0)));
        return result;
    }

    private JSONObject lessonAt(JSONArray array, String date, int period) {
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null && date.equals(item.optString("date"))
                    && period == item.optInt("effectivePeriodNo", 0)) return item;
        }
        return null;
    }

    private String lessonText(JSONObject lesson) {
        JSONObject entry = lesson.optJSONObject("entry");
        String course = entry == null ? "课程" : entry.optString("courseName", "课程");
        String room = entry == null ? "教室待定" : entry.optString("classroomName", "教室待定");
        String teacher = lesson.optString("effectiveTeacherName", "");
        String status = statusName(lesson.optString("occurrenceStatus", ""));
        return course + "\n" + timeOf(lesson) + "\n" + teacher + " " + room
                + ("正常".equals(status) ? "" : "\n" + status);
    }

    private String timeOf(JSONObject lesson) {
        String start = lesson.optString("startTime", "");
        String end = lesson.optString("endTime", "");
        return start.isEmpty() ? "第 " + lesson.optInt("effectivePeriodNo") + " 节" : start + "-" + end;
    }

    private static String statusName(String status) {
        switch (status) {
            case "CANCELLED": return "停课";
            case "MOVED_OUT": return "已调出";
            case "MOVED_IN": return "调入";
            case "SUBSTITUTED": return "代课";
            case "MAKEUP": return "补课";
            default: return "正常";
        }
    }

    private JSONObject findApplication(String... keys) {
        for (int i = 0; i < portal.applications.length(); i++) {
            JSONObject application = portal.applications.optJSONObject(i);
            String value = (application.optString("code") + " " + application.optString("name")
                    + " " + application.optString("routePath")).toLowerCase(Locale.ROOT);
            for (String key : keys) if (value.contains(key.toLowerCase(Locale.ROOT))) return application;
        }
        return null;
    }

    private List<String> applicationNames(String... keys) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < portal.applications.length(); i++) {
            JSONObject app = portal.applications.optJSONObject(i);
            String value = (app.optString("code") + " " + app.optString("name")
                    + " " + app.optString("routePath")).toLowerCase(Locale.ROOT);
            for (String key : keys) {
                if (value.contains(key.toLowerCase(Locale.ROOT))) {
                    names.add(app.optString("name", "应用"));
                    break;
                }
            }
        }
        return names;
    }

    private List<JSONObject> objects(Object data) {
        JSONArray array;
        if (data instanceof JSONArray) array = (JSONArray) data;
        else if (data instanceof JSONObject) {
            JSONObject object = (JSONObject) data;
            array = object.optJSONArray("content");
            if (array == null) array = object.optJSONArray("records");
            if (array == null) return Collections.singletonList(object);
        } else return Collections.emptyList();
        List<JSONObject> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject value = array.optJSONObject(i);
            if (value != null) values.add(value);
        }
        return values;
    }

    private String workspaceLabel() {
        switch (portal.workspace) {
            case STUDENT: return "学生";
            case TEACHER: return "教师";
            case MANAGEMENT: return "校领导 / 管理人员";
            default: return "教职工";
        }
    }

    private String scheduleKey() {
        return "schedule_expanded_" + portal.user.optString("username", "current");
    }

    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour < 11 ? "早上好" : hour < 14 ? "中午好" : hour < 18 ? "下午好" : "晚上好";
    }

    private String dateLabel(Date date) {
        return new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(date);
    }

    private static String first(JSONObject value, String... keys) {
        for (String key : keys) {
            String item = value.optString(key, "");
            if (!item.isEmpty()) return item.length() > 100 ? item.substring(0, 100) + "…" : item;
        }
        return "详情";
    }

    private static String join(List<String> values, String separator) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(separator);
            result.append(value);
        }
        return result.toString();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private String message(Exception error, String fallback) {
        if (error instanceof ApiClient.ApiException && ((ApiClient.ApiException) error).status == 401) {
            api.clear();
            return "会话已过期，请重新登录";
        }
        return error.getMessage() == null || error.getMessage().isEmpty() ? fallback : error.getMessage();
    }

    private void setBusy(Button button, boolean busy, String title) {
        button.setEnabled(!busy);
        button.setText(title);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private <T> void run(ThrowingSupplier<T> work, Success<T> success, Failure failure) {
        new Thread(() -> {
            try {
                T result = work.get();
                runOnUiThread(() -> success.accept(result));
            } catch (Exception error) {
                runOnUiThread(() -> failure.accept(error));
            }
        }).start();
    }

    private interface ThrowingSupplier<T> { T get() throws Exception; }
    private interface Success<T> { void accept(T value); }
    private interface Failure { void accept(Exception error); }
    private interface RowMapper { List<String> rows(Object data); }
}
