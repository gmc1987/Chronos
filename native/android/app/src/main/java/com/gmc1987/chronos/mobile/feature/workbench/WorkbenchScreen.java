package com.gmc1987.chronos.mobile.feature.workbench;

import android.content.SharedPreferences;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.DateFormats;
import com.gmc1987.chronos.mobile.core.common.ErrorMessages;
import com.gmc1987.chronos.mobile.core.common.RowFormatters;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.model.PortalState;
import com.gmc1987.chronos.mobile.feature.schedule.ScheduleRepository;
import com.gmc1987.chronos.mobile.feature.schedule.ScheduleView;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.function.Consumer;

public final class WorkbenchScreen {
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final SharedPreferences preferences;
    private final PortalState portal;
    private final ScheduleRepository schedules;
    private final MeetingsRepository meetings;
    private final SupervisionRepository supervision;
    private final Runnable applications;
    private final Consumer<JSONObject> courseDetails;
    private final Consumer<JSONObject> meetingDetails;
    private final Consumer<String> workflowDetails;
    private final Consumer<JSONObject> itemDetails;

    public WorkbenchScreen(
            UiKit ui,
            AsyncRunner async,
            ScreenHost host,
            SharedPreferences preferences,
            PortalState portal,
            ScheduleRepository schedules,
            MeetingsRepository meetings,
            SupervisionRepository supervision,
            Runnable applications,
            Consumer<JSONObject> courseDetails,
            Consumer<JSONObject> meetingDetails,
            Consumer<String> workflowDetails,
            Consumer<JSONObject> itemDetails) {
        this.ui = ui;
        this.async = async;
        this.host = host;
        this.preferences = preferences;
        this.portal = portal;
        this.schedules = schedules;
        this.meetings = meetings;
        this.supervision = supervision;
        this.applications = applications;
        this.courseDetails = courseDetails;
        this.meetingDetails = meetingDetails;
        this.workflowDetails = workflowDetails;
        this.itemDetails = itemDetails;
    }

    public void show() {
        LinearLayout page = ui.page(title());
        page.addView(summaryCard());
        if (portal.workspace() == PortalState.Workspace.STUDENT
                || portal.workspace() == PortalState.Workspace.TEACHER) {
            page.addView(new ScheduleView(
                    ui, async, preferences, schedules, portal, this::show,
                    courseDetails).build());
        }
        if (portal.workspace() == PortalState.Workspace.STUDENT) addLearningCards(page);
        else addStaffCards(page);
        host.show(ui.scroll(page));
    }

    private LinearLayout summaryCard() {
        LinearLayout card = ui.card();
        LinearLayout identity = ui.horizontal();
        String displayName = portal.displayName();
        String initial = displayName == null || displayName.isEmpty()
                ? "C" : displayName.substring(0, 1);
        identity.addView(ui.badge(initial, 54));
        LinearLayout copy = ui.vertical();
        copy.setPadding(ui.dp(15), 0, 0, 0);
        TextView eyebrow = ui.label(title(), 12, UiKit.BRAND, true);
        eyebrow.setLetterSpacing(.08f);
        copy.addView(eyebrow);
        copy.addView(ui.label(greeting() + "，" + displayName, 22, UiKit.TEXT, true));
        if (!portal.organization().isEmpty()) {
            copy.addView(ui.label(portal.organization(), 14, UiKit.MUTED, false));
        }
        if (portal.workspace() == PortalState.Workspace.STUDENT) {
            copy.addView(ui.label("班级信息待服务端学生摘要补充", 13, UiKit.MUTED, false));
        }
        copy.addView(ui.label(portal.termName() + " · " + DateFormats.display(new Date()),
                13, UiKit.MUTED, false));
        identity.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(identity);
        return card;
    }

    private void addLearningCards(LinearLayout page) {
        addCapability(page, "课件", "查看课程课件与最近更新", "课件", "courseware", "material");
        addCapability(page, "错题沉淀", "回顾错题与待掌握内容", "错题", "error-book", "wrong");
        addCapability(page, "题库", "进入授权练习与题库", "题库", "question", "exercise");
    }

    private void addCapability(
            LinearLayout page, String title, String description, String... keys) {
        JSONObject application = portal.findApplication(keys);
        LinearLayout card = ui.card();
        card.addView(ui.label(title, 17, UiKit.TEXT, true));
        card.addView(ui.label(application == null
                        ? "建设中 · 当前服务端未开放移动入口" : description,
                13, application == null ? UiKit.MUTED : UiKit.BRAND, false));
        if (application != null) {
            android.widget.Button open = ui.secondaryButton("在应用中心查看");
            open.setOnClickListener(view -> applications.run());
            card.addView(open);
        }
        page.addView(card);
    }

    private void addStaffCards(LinearLayout page) {
        addContributionCard(page, "我的待办", "WORKFLOW", "当前没有待处理事项");
        addMeetingsCard(page);
        if (portal.workspace() == PortalState.Workspace.MANAGEMENT) {
            addSupervisionCard(page);
            addMetricsCard(page);
            return;
        }
        addApplicationCard(page, "协同办公",
                "collaboration", "协同", "office", "leave", "trip");
        if (portal.workspace() == PortalState.Workspace.TEACHER) {
            addApplicationCard(page, "教学中心", "education", "教学", "题库", "课件");
        } else {
            addContributionCard(page, "公告摘要", "ANNOUNCEMENT", "当前没有可见公告");
        }
    }

    private void addContributionCard(
            LinearLayout page, String title, String code, String emptyText) {
        JSONObject contribution = portal.contribution(code);
        LinearLayout card = ui.card();
        card.addView(ui.label(title, 17, UiKit.TEXT, true));
        if (contribution.length() > 0 && contribution.optBoolean("available", false)) {
            JSONObject data = contribution.optJSONObject("data");
            JSONArray items = data == null ? null : data.optJSONArray("items");
            if (items == null || items.length() == 0) {
                card.addView(ui.label(emptyText, 13, UiKit.MUTED, false));
            } else for (int i = 0; i < Math.min(3, items.length()); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                LinearLayout row = ui.vertical();
                row.addView(ui.label(item.optString("title", item.optString("flowName", "事项")),
                        14, UiKit.TEXT, true));
                row.addView(ui.label(item.optString("summary", item.optString("nodeName", "点击查看详情")),
                        12, UiKit.MUTED, false));
                row.setClickable(true);
                row.setOnClickListener(view -> {
                    String instanceId = item.optString("instanceId");
                    if (instanceId.isEmpty()) itemDetails.accept(item);
                    else workflowDetails.accept(instanceId);
                });
                card.addView(row);
            }
        } else {
            card.addView(ui.label("建设中 · " + (contribution.length() == 0
                    ? "服务端尚未提供摘要接口"
                    : contribution.optString("message", "服务暂不可用")), 13, UiKit.MUTED, false));
        }
        page.addView(card);
    }

    private void addRemoteCard(
            LinearLayout page,
            String title,
            String emptyText,
            Loader loader,
            Formatter formatter) {
        LinearLayout card = ui.card();
        card.addView(ui.label(title, 17, UiKit.TEXT, true));
        LinearLayout body = ui.vertical();
        body.addView(new ProgressBar(card.getContext()));
        card.addView(body);
        page.addView(card);
        async.run(loader::load, response -> {
            body.removeAllViews();
            List<String> rows = formatter.rows(response.opt("data"));
            if (rows.isEmpty()) body.addView(ui.empty(emptyText));
            else for (String row : rows.subList(0, Math.min(3, rows.size()))) {
                body.addView(ui.label("• " + row, 13, UiKit.TEXT, false));
            }
        }, error -> {
            body.removeAllViews();
            body.addView(ui.error(ErrorMessages.display(error, title + "加载失败"), this::show));
        });
    }

    private void addMeetingsCard(LinearLayout page) {
        LinearLayout card = ui.card();
        card.addView(ui.label("近期会议", 17, UiKit.TEXT, true));
        LinearLayout body = ui.vertical();
        body.addView(new ProgressBar(card.getContext()));
        card.addView(body);
        page.addView(card);
        async.run(meetings::list, response -> {
            body.removeAllViews();
            JSONArray rows = response.optJSONArray("data");
            if (rows == null || rows.length() == 0) {
                body.addView(ui.empty("未来暂无会议"));
                return;
            }
            for (int i = 0; i < Math.min(3, rows.length()); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null || row.optJSONObject("meeting") == null) continue;
                JSONObject meeting = row.optJSONObject("meeting");
                LinearLayout item = ui.vertical();
                item.setPadding(0, ui.dp(8), 0, ui.dp(8));
                item.addView(ui.label(meeting.optString("title", "会议"), 14, UiKit.TEXT, true));
                item.addView(ui.label(meeting.optString("startTime", "") + " · 点击查看详情",
                        12, UiKit.MUTED, false));
                item.setClickable(true);
                item.setOnClickListener(view -> meetingDetails.accept(row));
                body.addView(item);
            }
        }, error -> {
            body.removeAllViews();
            body.addView(ui.error(ErrorMessages.display(error, "近期会议加载失败"), this::show));
        });
    }

    private void addSupervisionCard(LinearLayout page) {
        LinearLayout card = ui.card();
        card.addView(ui.label("督办事项", 17, UiKit.TEXT, true));
        LinearLayout body = ui.vertical();
        body.addView(new ProgressBar(card.getContext()));
        card.addView(body);
        page.addView(card);
        async.run(supervision::list, response -> {
            body.removeAllViews();
            Object data = response.opt("data");
            JSONArray rows = data instanceof JSONArray ? (JSONArray) data
                    : data instanceof JSONObject ? ((JSONObject) data).optJSONArray("content") : null;
            if (rows == null || rows.length() == 0) {
                body.addView(ui.empty("当前没有督办事项"));
                return;
            }
            for (int i = 0; i < Math.min(3, rows.length()); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                LinearLayout item = ui.vertical();
                item.addView(ui.label(row.optString("title", row.optString("name", "督办事项")),
                        14, UiKit.TEXT, true));
                item.addView(ui.label(row.optString("status", "点击查看详情"),
                        12, UiKit.MUTED, false));
                item.setClickable(true);
                item.setOnClickListener(view -> itemDetails.accept(row));
                body.addView(item);
            }
        }, error -> {
            body.removeAllViews();
            body.addView(ui.error(ErrorMessages.display(error, "督办事项加载失败"), this::show));
        });
    }

    private void addApplicationCard(LinearLayout page, String title, String... keys) {
        LinearLayout card = ui.card();
        card.addView(ui.label(title, 17, UiKit.TEXT, true));
        List<String> names = portal.applicationNames(keys);
        card.addView(ui.label(names.isEmpty()
                ? "当前账号暂无授权应用" : String.join(" · ", names), 13, UiKit.MUTED, false));
        if (!names.isEmpty()) {
            card.setClickable(true);
            card.setOnClickListener(view -> applications.run());
        }
        page.addView(card);
    }

    private void addMetricsCard(LinearLayout page) {
        LinearLayout card = ui.card();
        card.addView(ui.label("数据概览", 17, UiKit.TEXT, true));
        JSONArray metrics = portal.education().optJSONArray("metrics");
        if (metrics == null || metrics.length() == 0) card.addView(ui.empty("当前没有授权指标"));
        else for (int i = 0; i < metrics.length(); i++) {
            JSONObject metric = metrics.optJSONObject(i);
            card.addView(ui.label(metric.optString("label", "指标") + "  "
                    + metric.optString("value", "—"), 15, UiKit.TEXT, false));
        }
        page.addView(card);
    }

    private String title() {
        switch (portal.workspace()) {
            case STUDENT: return "学生工作台";
            case PARENT: return "家长工作台";
            case TEACHER: return "教师工作台";
            case MANAGEMENT: return "管理工作台";
            default: return "职工工作台";
        }
    }

    private static String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour < 11 ? "早上好" : hour < 14 ? "中午好" : hour < 18 ? "下午好" : "晚上好";
    }

    private interface Loader { JSONObject load() throws Exception; }
    private interface Formatter { List<String> rows(Object data); }
}
