package com.gmc1987.chronos.mobile.feature.workbench;

import android.app.AlertDialog;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native meeting detail with invitation response and check-in actions. */
public final class MeetingDetailScreen {
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final MeetingsRepository meetings;
    private final Runnable back;
    private JSONObject value;

    public MeetingDetailScreen(UiKit ui, AsyncRunner async, ScreenHost host,
            MeetingsRepository meetings, JSONObject value, Runnable back) {
        this.ui = ui;
        this.async = async;
        this.host = host;
        this.meetings = meetings;
        this.value = value;
        this.back = back;
    }

    public void show() {
        JSONObject meeting = value.optJSONObject("meeting");
        if (meeting == null) meeting = value;
        LinearLayout page = ui.page("会议详情");
        Button backButton = ui.secondaryButton("‹ 返回工作台");
        backButton.setOnClickListener(view -> back.run());
        page.addView(backButton);
        detail(page, "主题", meeting.optString("title", "—"));
        detail(page, "开始时间", meeting.optString("startTime", "—"));
        detail(page, "结束时间", meeting.optString("endTime", "—"));
        detail(page, "组织者", meeting.optString("organizerUsername", "—"));
        detail(page, "会议形式", meeting.optString("meetingType", "—"));
        JSONObject room = value.optJSONObject("room");
        detail(page, "地点", room == null ? "—" : room.optString("roomName", "")
                + " " + room.optString("location", ""));
        detail(page, "议程", meeting.optString("agenda", "—"));
        JSONObject participant = value.optJSONObject("currentParticipant");
        detail(page, "参会状态", participant == null ? "组织者"
                : responseLabel(participant.optString("responseStatus")));

        if ("PUBLISHED".equals(meeting.optString("status")) && participant != null) {
            EditText comment = ui.input("谢绝或请假原因", false);
            page.addView(comment);
            addAction(page, "确认参加", () -> respond("ACCEPTED", comment.getText().toString()));
            addAction(page, "谢绝参加", () -> respond("DECLINED", comment.getText().toString()));
            addAction(page, "会议请假", () -> {
                String reason = comment.getText().toString().trim();
                if (reason.isEmpty()) showError("会议请假必须填写原因");
                else respond("LEAVE", reason);
            });
            Button checkIn = ui.secondaryButton(participant.optString("checkedInAt").isEmpty()
                    ? "会议签到" : "已签到");
            checkIn.setEnabled(participant.optString("checkedInAt").isEmpty());
            checkIn.setOnClickListener(view -> checkIn());
            page.addView(checkIn);
        }
        host.show(ui.scroll(page));
    }

    private void detail(LinearLayout page, String label, String text) {
        LinearLayout card = ui.card();
        card.addView(ui.label(label, 13, UiKit.MUTED, false));
        card.addView(ui.label(text.trim().isEmpty() ? "—" : text.trim(), 16, UiKit.TEXT, false));
        page.addView(card);
    }

    private void addAction(LinearLayout page, String title, Runnable action) {
        Button button = ui.secondaryButton(title);
        button.setOnClickListener(view -> action.run());
        page.addView(button);
    }

    private void respond(String status, String comment) {
        String id = value.optJSONObject("meeting").optString("id");
        async.run(() -> meetings.respond(id, status, comment), ignored -> reload(id),
                error -> showError(error.getMessage()));
    }

    private void checkIn() {
        String id = value.optJSONObject("meeting").optString("id");
        async.run(() -> meetings.checkIn(id), ignored -> reload(id),
                error -> showError(error.getMessage()));
    }

    private void reload(String id) {
        async.run(meetings::list, response -> {
            JSONArray rows = response.optJSONArray("data");
            if (rows != null) for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row != null && id.equals(row.optJSONObject("meeting").optString("id"))) {
                    value = row;
                    break;
                }
            }
            show();
        }, error -> showError(error.getMessage()));
    }

    private void showError(String message) {
        new AlertDialog.Builder(ui.card().getContext()).setTitle("操作失败")
                .setMessage(message == null ? "请求失败" : message).setPositiveButton("知道了", null).show();
    }

    private static String responseLabel(String status) {
        switch (status) {
            case "ACCEPTED": return "已接受";
            case "DECLINED": return "已谢绝";
            case "LEAVE": return "已请假";
            default: return "待确认";
        }
    }
}
