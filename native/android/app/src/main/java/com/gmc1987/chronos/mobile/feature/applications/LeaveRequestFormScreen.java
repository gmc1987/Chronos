package com.gmc1987.chronos.mobile.feature.applications;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import java.util.Calendar;
import org.json.JSONObject;

/** Native leave request form matching the iOS fields and workflow endpoint. */
public final class LeaveRequestFormScreen {
    private final Activity activity;
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final HttpClient http;
    private final Runnable back;

    public LeaveRequestFormScreen(Activity activity, UiKit ui, AsyncRunner async, ScreenHost host,
            HttpClient http, Runnable back) {
        this.activity = activity;
        this.ui = ui;
        this.async = async;
        this.host = host;
        this.http = http;
        this.back = back;
    }

    public void show() {
        ApplicationScreenChrome chrome = new ApplicationScreenChrome(ui, host);
        LinearLayout page = chrome.page("发起请假", back);
        LinearLayout card = ui.card();
        card.addView(ui.label("请假信息", 18, UiKit.TEXT, true));
        Spinner type = new Spinner(activity);
        type.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"事假", "病假", "年假"}));
        card.addView(type);
        Calendar startValue = Calendar.getInstance();
        Calendar endValue = Calendar.getInstance();
        NativeDateField start = new NativeDateField(activity, ui, "开始日期", startValue, false);
        NativeDateField end = new NativeDateField(activity, ui, "结束日期", endValue, false);
        card.addView(start.view());
        card.addView(end.view());
        EditText reason = ui.input("请假原因", false);
        reason.setSingleLine(false);
        reason.setMinLines(3);
        card.addView(reason);
        page.addView(card);
        Button submit = ui.primaryButton("提交请假申请");
        submit.setOnClickListener(view -> submit(type, start, end, reason, submit));
        page.addView(submit);
        host.show(ui.scroll(page));
    }

    private void submit(Spinner type, NativeDateField start, NativeDateField end,
            EditText reason, Button button) {
        String note = reason.getText().toString().trim();
        if (note.isEmpty()) {
            new ApplicationScreenChrome(ui, host).dialog("请完善表单", "请填写请假原因");
            return;
        }
        if (end.millis() < start.millis()) {
            new ApplicationScreenChrome(ui, host).dialog("请完善表单", "结束日期不能早于开始日期");
            return;
        }
        try {
            String[] codes = {"PERSONAL", "SICK", "ANNUAL"};
            JSONObject body = new JSONObject().put("leaveType", codes[type.getSelectedItemPosition()])
                    .put("startDate", start.date()).put("endDate", end.date()).put("reason", note);
            button.setEnabled(false);
            async.run(() -> http.post("/portal/education/leaves", body, true), response ->
                    new AlertDialog.Builder(activity).setTitle("提交成功")
                            .setMessage("请假申请已进入审批流程。")
                            .setPositiveButton("完成", (dialog, which) -> back.run()).show(), error -> {
                                button.setEnabled(true);
                                new ApplicationScreenChrome(ui, host).dialog("提交失败",
                                        ApplicationJson.message(error));
                            });
        } catch (Exception error) {
            new ApplicationScreenChrome(ui, host).dialog("提交失败", ApplicationJson.message(error));
        }
    }
}
