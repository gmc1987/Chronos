package com.gmc1987.chronos.mobile.feature.applications;

import android.app.Activity;
import android.widget.Button;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native leave request list with a dedicated creation screen. */
public final class LeaveRequestsScreen {
    private final Activity activity;
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final HttpClient http;
    private final Runnable back;

    public LeaveRequestsScreen(Activity activity, UiKit ui, AsyncRunner async, ScreenHost host,
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
        chrome.loading("正在加载请假记录…");
        async.run(() -> ApplicationJson.rows(http.get("/portal/education/leaves")), rows -> {
            LinearLayout page = chrome.page("请假", back);
            Button create = ui.primaryButton("发起请假");
            create.setOnClickListener(view -> new LeaveRequestFormScreen(
                    activity, ui, async, host, http, this::show).show());
            page.addView(create);
            renderRows(page, rows);
            host.show(ui.scroll(page));
        }, error -> chrome.error("请假", error, this::show, back));
    }

    private void renderRows(LinearLayout page, JSONArray rows) {
        if (rows.length() == 0) {
            page.addView(ui.empty("暂无请假记录"));
            return;
        }
        NativeRecordViews records = new NativeRecordViews(ui);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            LinearLayout card = records.card(row);
            card.setClickable(true);
            card.setOnClickListener(view -> new NativeRecordDetailScreen(
                    ui, host, "请假", row, this::show).show());
            card.addView(ui.label("查看详情  ›", 13, UiKit.BRAND, true));
            page.addView(card);
        }
    }
}
