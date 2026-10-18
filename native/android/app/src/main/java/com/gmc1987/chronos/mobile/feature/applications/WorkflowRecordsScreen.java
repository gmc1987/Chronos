package com.gmc1987.chronos.mobile.feature.applications;

import android.app.Activity;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native workflow record list for pending, handled and initiated instances. */
public final class WorkflowRecordsScreen {
    private final Activity activity; private final UiKit ui; private final AsyncRunner async;
    private final ScreenHost host; private final HttpClient http; private final String title;
    private final String path; private final String empty; private final Runnable back;

    public WorkflowRecordsScreen(Activity activity, UiKit ui, AsyncRunner async, ScreenHost host,
            HttpClient http, String title, String path, String empty, Runnable back) {
        this.activity = activity; this.ui = ui; this.async = async; this.host = host;
        this.http = http; this.title = title; this.path = path; this.empty = empty; this.back = back;
    }

    public void show() {
        ApplicationScreenChrome chrome = new ApplicationScreenChrome(ui, host);
        chrome.loading("正在加载流程记录…");
        async.run(() -> ApplicationJson.rows(http.get(path)), this::render,
                error -> chrome.error(title, error, this::show, back));
    }

    private void render(JSONArray rows) {
        LinearLayout page = new ApplicationScreenChrome(ui, host).page(title, back);
        if (rows.length() == 0) page.addView(ui.empty(empty));
        NativeRecordViews records = new NativeRecordViews(ui);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i); if (row == null) continue;
            LinearLayout card = records.card(row);
            String instanceId = row.optString("instanceId",
                    row.optString("processInstanceId", row.optString("id")));
            if (!instanceId.isEmpty()) {
                card.setClickable(true);
                card.setOnClickListener(view -> new WorkflowInstanceScreen(
                        activity, ui, async, host, http, instanceId, this::show).show());
                card.addView(ui.label("查看流程详情  ›", 13, UiKit.BRAND, true));
            }
            page.addView(card);
        }
        host.show(ui.scroll(page));
    }
}
