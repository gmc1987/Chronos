package com.gmc1987.chronos.mobile.feature.applications;

import android.app.Activity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import java.util.Calendar;
import org.json.JSONObject;

/** Request detail with withdraw, trip cancellation and leave cancellation actions. */
public final class RequestActionDetailScreen {
    public enum Kind { LEAVE, BUSINESS_TRIP, OFFICE }
    private final Activity activity; private final UiKit ui; private final AsyncRunner async;
    private final ScreenHost host; private final HttpClient http; private final String title;
    private final JSONObject record; private final Kind kind; private final Runnable back;

    public RequestActionDetailScreen(Activity activity, UiKit ui, AsyncRunner async,
            ScreenHost host, HttpClient http, String title, JSONObject record, Kind kind,
            Runnable back) {
        this.activity = activity; this.ui = ui; this.async = async; this.host = host;
        this.http = http; this.title = title; this.record = record; this.kind = kind; this.back = back;
    }

    public void show() {
        LinearLayout page = new ApplicationScreenChrome(ui, host).page(title + "详情", back);
        new NativeRecordViews(ui).appendDetail(page, record, "");
        String status = record.optString("status");
        if ("PENDING".equals(status)) addWithdraw(page);
        if ("APPROVED".equals(status) && kind == Kind.BUSINESS_TRIP) addTripCancel(page);
        if ("APPROVED".equals(status) && kind == Kind.LEAVE) addLeaveCancel(page);
        host.show(ui.scroll(page));
    }

    private void addWithdraw(LinearLayout page) {
        EditText reason = ui.input("撤回原因（可选）", false);
        Button action = ui.destructiveButton("撤回申请");
        action.setOnClickListener(ignored -> {
            String path = kind == Kind.LEAVE ? "/portal/education/leaves/"
                    : kind == Kind.BUSINESS_TRIP ? "/portal/collaboration/business-trips/"
                    : "/portal/collaboration/requests/";
            JSONObject body = kind == Kind.OFFICE ? new JSONObject()
                    : json("reason", reason.getText().toString().trim());
            execute(path + record.optString("id") + "/withdraw", body, "申请已撤回");
        });
        page.addView(reason); page.addView(action);
    }

    private void addTripCancel(LinearLayout page) {
        EditText reason = ui.input("取消原因", false);
        Button action = ui.destructiveButton("取消已批准的出差");
        action.setOnClickListener(ignored -> {
            String value = reason.getText().toString().trim();
            if (value.isEmpty()) { new ApplicationScreenChrome(ui, host)
                    .dialog("请完善信息", "请填写取消原因"); return; }
            execute("/portal/collaboration/business-trips/" + record.optString("id") + "/cancel",
                    json("reason", value), "出差已取消");
        });
        page.addView(reason); page.addView(action);
    }

    private void addLeaveCancel(LinearLayout page) {
        EditText reason = ui.input("销假原因", false);
        NativeDateField actualEnd = new NativeDateField(activity, ui, "实际结束日期",
                Calendar.getInstance(), false);
        Button action = ui.primaryButton("提交销假申请");
        action.setOnClickListener(ignored -> {
            String value = reason.getText().toString().trim();
            if (value.isEmpty()) { new ApplicationScreenChrome(ui, host)
                    .dialog("请完善信息", "请填写销假原因"); return; }
            execute("/portal/education/leaves/" + record.optString("id") + "/cancellation",
                    json("reason", value, "actualEndDate", actualEnd.date()),
                    "销假申请已提交");
        });
        page.addView(reason); page.addView(actualEnd.view()); page.addView(action);
    }

    private void execute(String path, JSONObject body, String message) {
        ApplicationScreenChrome chrome = new ApplicationScreenChrome(ui, host);
        chrome.loading("正在处理…");
        async.run(() -> http.post(path, body, true),
                ignored -> chrome.message("处理成功", message, back),
                error -> chrome.error("处理失败", error, this::show, back));
    }

    private static JSONObject json(String... pairs) {
        JSONObject value = new JSONObject();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            try { value.put(pairs[i], pairs[i + 1]); } catch (Exception ignored) {}
        }
        return value;
    }
}
