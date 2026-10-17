package com.gmc1987.chronos.mobile.feature.schedule;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.DateFormats;
import com.gmc1987.chronos.mobile.core.common.ErrorMessages;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.model.PortalState;
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

public final class ScheduleView {
    private final UiKit ui;
    private final AsyncRunner async;
    private final SharedPreferences preferences;
    private final ScheduleRepository repository;
    private final PortalState portal;
    private final Runnable refreshDashboard;
    private Calendar weekStart = DateFormats.weekStart(new Date());

    public ScheduleView(
            UiKit ui,
            AsyncRunner async,
            SharedPreferences preferences,
            ScheduleRepository repository,
            PortalState portal,
            Runnable refreshDashboard) {
        this.ui = ui;
        this.async = async;
        this.preferences = preferences;
        this.repository = repository;
        this.portal = portal;
        this.refreshDashboard = refreshDashboard;
    }

    public View build() {
        LinearLayout card = ui.card();
        LinearLayout heading = ui.horizontal();
        heading.addView(ui.label("本周课表", 18, UiKit.TEXT, true),
                new LinearLayout.LayoutParams(0, ui.dp(48), 1));
        boolean expanded = preferences.getBoolean(preferenceKey(), true);
        Button toggle = ui.secondaryButton(expanded ? "收起" : "展开");
        heading.addView(toggle);
        card.addView(heading);
        card.addView(ui.label(range(), 13, UiKit.MUTED, false));
        LinearLayout body = ui.vertical();
        card.addView(body);
        toggle.setOnClickListener(view -> {
            preferences.edit().putBoolean(preferenceKey(), !expanded).apply();
            refreshDashboard.run();
        });
        load(body, expanded);
        return card;
    }

    private void load(LinearLayout body, boolean expanded) {
        body.removeAllViews();
        body.addView(new ProgressBar(body.getContext()));
        async.run(() -> repository.calendar(
                DateFormats.iso(weekStart.getTime()),
                DateFormats.iso(DateFormats.weekEnd(weekStart).getTime())), response -> {
            body.removeAllViews();
            JSONObject data = response.optJSONObject("data");
            JSONArray occurrences = data == null ? null : data.optJSONArray("occurrences");
            if (!expanded) {
                body.addView(collapsed(occurrences));
                return;
            }
            addControls(body);
            body.addView(occurrences == null || occurrences.length() == 0
                    ? ui.empty("本周暂无课程") : grid(occurrences));
        }, error -> {
            body.removeAllViews();
            body.addView(ui.error(ErrorMessages.display(error, "个人课表加载失败"),
                    () -> load(body, expanded)));
        });
    }

    private void addControls(LinearLayout body) {
        LinearLayout controls = ui.horizontal();
        Button previous = ui.secondaryButton("上一周");
        Button current = ui.secondaryButton("本周");
        Button next = ui.secondaryButton("下一周");
        controls.addView(previous, weighted());
        controls.addView(current, weighted());
        controls.addView(next, weighted());
        previous.setOnClickListener(view -> move(-7));
        current.setOnClickListener(view -> {
            weekStart = DateFormats.weekStart(new Date());
            refreshDashboard.run();
        });
        next.setOnClickListener(view -> move(7));
        body.addView(controls);
    }

    private View collapsed(JSONArray occurrences) {
        List<JSONObject> today = occurrencesForDate(occurrences, DateFormats.iso(new Date()));
        LinearLayout summary = ui.vertical();
        summary.addView(ui.label(today.isEmpty()
                ? "今日无课程" : "今日 " + today.size() + " 节课", 15, UiKit.TEXT, true));
        if (!today.isEmpty()) {
            JSONObject next = today.get(0);
            JSONObject entry = next.optJSONObject("entry");
            summary.addView(ui.label("下一节：" + (entry == null
                    ? "课程" : entry.optString("courseName", "课程"))
                    + " · " + time(next), 13, UiKit.MUTED, false));
        }
        return summary;
    }

    private View grid(JSONArray occurrences) {
        List<Integer> periods = new ArrayList<>();
        for (int i = 0; i < occurrences.length(); i++) {
            int period = occurrences.optJSONObject(i).optInt("effectivePeriodNo", 0);
            if (period > 0 && !periods.contains(period)) periods.add(period);
        }
        Collections.sort(periods);
        LinearLayout table = ui.vertical();
        table.setBackgroundColor(Color.rgb(225, 232, 233));
        LinearLayout header = ui.horizontal();
        header.addView(cell("节次", true, 76));
        Calendar day = (Calendar) weekStart.clone();
        for (int column = 0; column < 7; column++) {
            header.addView(cell("周" + "一二三四五六日".charAt(column) + "\n"
                    + new SimpleDateFormat("MM-dd", Locale.ROOT).format(day.getTime()), true, 120));
            day.add(Calendar.DATE, 1);
        }
        table.addView(header);
        for (int period : periods) table.addView(periodRow(occurrences, period));
        HorizontalScrollView scroll = new HorizontalScrollView(table.getContext());
        scroll.addView(table);
        return scroll;
    }

    private View periodRow(JSONArray occurrences, int period) {
        LinearLayout row = ui.horizontal();
        row.addView(cell("第 " + period + " 节", true, 76));
        Calendar date = (Calendar) weekStart.clone();
        for (int column = 0; column < 7; column++) {
            JSONObject lesson = lessonAt(occurrences, DateFormats.iso(date.getTime()), period);
            row.addView(cell(lesson == null ? "—" : lessonText(lesson), false, 120));
            date.add(Calendar.DATE, 1);
        }
        return row;
    }

    private View cell(String value, boolean heading, int width) {
        TextView cell = ui.label(value, heading ? 12 : 11,
                heading ? UiKit.BRAND_DARK : UiKit.TEXT, heading);
        cell.setGravity(Gravity.CENTER);
        cell.setBackgroundColor(Color.WHITE);
        cell.setMinHeight(ui.dp(76));
        cell.setMaxLines(5);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ui.dp(width), -2);
        params.setMargins(0, 0, ui.dp(1), ui.dp(1));
        cell.setLayoutParams(params);
        return cell;
    }

    private String lessonText(JSONObject lesson) {
        JSONObject entry = lesson.optJSONObject("entry");
        String course = entry == null ? "课程" : entry.optString("courseName", "课程");
        String room = entry == null ? "教室待定" : entry.optString("classroomName", "教室待定");
        String status = statusName(lesson.optString("occurrenceStatus", ""));
        return course + "\n" + time(lesson) + "\n"
                + lesson.optString("effectiveTeacherName", "") + " " + room
                + ("正常".equals(status) ? "" : "\n" + status);
    }

    private static List<JSONObject> occurrencesForDate(JSONArray array, String date) {
        List<JSONObject> result = new ArrayList<>();
        if (array == null) return result;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null && date.equals(item.optString("date"))) result.add(item);
        }
        result.sort(Comparator.comparingInt(value -> value.optInt("effectivePeriodNo", 0)));
        return result;
    }

    private static JSONObject lessonAt(JSONArray array, String date, int period) {
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null && date.equals(item.optString("date"))
                    && period == item.optInt("effectivePeriodNo", 0)) return item;
        }
        return null;
    }

    private static String time(JSONObject lesson) {
        String start = lesson.optString("startTime", "");
        return start.isEmpty() ? "第 " + lesson.optInt("effectivePeriodNo") + " 节"
                : start + "-" + lesson.optString("endTime", "");
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

    private void move(int days) {
        weekStart.add(Calendar.DATE, days);
        refreshDashboard.run();
    }

    private String range() {
        return DateFormats.iso(weekStart.getTime()) + " 至 "
                + DateFormats.iso(DateFormats.weekEnd(weekStart).getTime());
    }

    private String preferenceKey() {
        return "schedule_expanded_" + portal.user().optString("username", "current");
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ui.dp(48), 1);
    }
}
