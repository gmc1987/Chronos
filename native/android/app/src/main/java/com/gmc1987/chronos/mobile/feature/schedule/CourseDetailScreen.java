package com.gmc1987.chronos.mobile.feature.schedule;

import android.widget.Button;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import org.json.JSONObject;

/** Displays one concrete calendar occurrence, including adjustments and substitutions. */
public final class CourseDetailScreen {
    private final UiKit ui;
    private final ScreenHost host;
    private final JSONObject occurrence;
    private final Runnable back;

    public CourseDetailScreen(UiKit ui, ScreenHost host, JSONObject occurrence, Runnable back) {
        this.ui = ui;
        this.host = host;
        this.occurrence = occurrence;
        this.back = back;
    }

    public void show() {
        JSONObject entry = occurrence.optJSONObject("entry");
        if (entry == null) entry = new JSONObject();
        LinearLayout page = ui.page("课程详情");
        Button backButton = ui.secondaryButton("返回课表");
        backButton.setOnClickListener(view -> back.run());
        page.addView(backButton);

        LinearLayout overview = ui.card();
        overview.addView(ui.label(entry.optString("courseName", "课程"), 21, UiKit.TEXT, true));
        addRow(overview, "上课日期", occurrence.optString("date", "待定"));
        addRow(overview, "上课时间", time(occurrence));
        addRow(overview, "上课教师", value(occurrence.optString("effectiveTeacherName", "")));
        addRow(overview, "任课老师", value(entry.optString("teacherName", "")));
        addRow(overview, "教学班", value(entry.optString("teachingClassName", "")));
        addRow(overview, "上课教室", value(entry.optString("classroomName", "")));
        addRow(overview, "课程状态", statusName(occurrence.optString("occurrenceStatus", "")));
        page.addView(overview);

        String reason = valueOrEmpty(occurrence, "reason");
        if (!reason.isEmpty()) {
            LinearLayout adjustment = ui.card();
            adjustment.addView(ui.label("调整说明", 17, UiKit.TEXT, true));
            adjustment.addView(ui.label(reason, 14, UiKit.MUTED, false));
            page.addView(adjustment);
        }
        host.show(ui.scroll(page));
    }

    private void addRow(LinearLayout card, String label, String value) {
        card.addView(ui.label(label + "：" + value, 14, UiKit.TEXT, false));
    }

    private static String value(String value) {
        return value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value)
                ? "待定" : value;
    }

    private static String valueOrEmpty(JSONObject source, String key) {
        return source.isNull(key) ? "" : source.optString(key, "");
    }

    private static String time(JSONObject lesson) {
        String start = lesson.optString("startTime", "");
        String end = lesson.optString("endTime", "");
        return start.isEmpty() ? "第 " + lesson.optInt("effectivePeriodNo") + " 节"
                : start + (end.isEmpty() ? "" : " - " + end)
                + "（第 " + lesson.optInt("effectivePeriodNo") + " 节）";
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
}
