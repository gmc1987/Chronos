package com.gmc1987.chronos.mobile.feature.exams;

import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ErrorMessages;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native teacher flow for acknowledging, checking in and requesting invigilation changes. */
public final class InvigilationScreen {
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final ExamsRepository repository;
    private final Runnable back;

    public InvigilationScreen(UiKit ui, AsyncRunner async, ScreenHost host,
            ExamsRepository repository, Runnable back) {
        this.ui = ui; this.async = async; this.host = host;
        this.repository = repository; this.back = back;
    }

    public void show() {
        host.setBackAction(back);
        LinearLayout page = ui.page("我的监考");
        LinearLayout body = ui.vertical();
        body.addView(ui.centered("正在加载监考安排…", true));
        page.addView(body);
        host.show(ui.scroll(page));
        async.run(repository::invigilations, response -> render(body, response), error -> {
            body.removeAllViews();
            body.addView(ui.error(ErrorMessages.display(error, "监考安排加载失败"), this::show));
        });
    }

    private void render(LinearLayout body, JSONObject response) {
        body.removeAllViews();
        JSONArray rows = response.optJSONArray("data");
        if (rows == null || rows.length() == 0) {
            body.addView(ui.empty("当前没有监考安排")); return;
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject duty = rows.optJSONObject(i);
            if (duty == null) continue;
            LinearLayout card = ui.card();
            card.addView(ui.label(duty.optString("subjectName", "考试监考"), 18, UiKit.TEXT, true));
            card.addView(ui.label(duty.optString("examDate") + " "
                    + duty.optString("startTime") + "-" + duty.optString("endTime"),
                    14, UiKit.TEXT, false));
            card.addView(ui.label(duty.optString("classroomName", "考场待定") + " · "
                    + duty.optString("dutyRole", "监考") + " · "
                    + duty.optString("assignmentStatus", duty.optString("status")),
                    13, UiKit.MUTED, false));
            String id = duty.optString("assignmentId");
            if (duty.isNull("acknowledgedAt")) addAction(card, "确认监考任务",
                    () -> repository.acknowledge(id));
            if (duty.isNull("checkedInAt")) addAction(card, "到场签到",
                    () -> repository.checkIn(id));
            EditText reason = ui.multilineInput("无法监考的原因", 3);
            card.addView(reason);
            addAction(card, "申请调换监考", () -> {
                String value = reason.getText().toString().trim();
                if (value.isEmpty()) throw new IllegalArgumentException("请填写调换原因");
                return repository.requestChange(id, value);
            });
            body.addView(card);
        }
    }

    private void addAction(LinearLayout card, String title, Action action) {
        Button button = ui.secondaryButton(title);
        button.setOnClickListener(view -> {
            button.setEnabled(false);
            async.run(action::run, ignored -> {
                Toast.makeText(view.getContext(), title + "成功", Toast.LENGTH_SHORT).show();
                show();
            }, error -> {
                button.setEnabled(true);
                Toast.makeText(view.getContext(), ErrorMessages.display(error, title + "失败"),
                        Toast.LENGTH_LONG).show();
            });
        });
        card.addView(button);
    }

    private interface Action { JSONObject run() throws Exception; }
}
