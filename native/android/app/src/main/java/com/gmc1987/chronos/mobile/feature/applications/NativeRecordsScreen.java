package com.gmc1987.chronos.mobile.feature.applications;

import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native read-only list for modules that expose records but no mobile editor. */
public final class NativeRecordsScreen {
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final HttpClient http;
    private final String title;
    private final String path;
    private final String empty;
    private final Runnable back;

    public NativeRecordsScreen(UiKit ui, AsyncRunner async, ScreenHost host, HttpClient http,
            String title, String path, String empty, Runnable back) {
        this.ui = ui;
        this.async = async;
        this.host = host;
        this.http = http;
        this.title = title;
        this.path = path;
        this.empty = empty;
        this.back = back;
    }

    public void show() {
        ApplicationScreenChrome chrome = new ApplicationScreenChrome(ui, host);
        chrome.loading("正在加载…");
        async.run(() -> ApplicationJson.rows(http.get(path)), this::render,
                error -> chrome.error(title, error, this::show, back));
    }

    private void render(JSONArray rows) {
        ApplicationScreenChrome chrome = new ApplicationScreenChrome(ui, host);
        LinearLayout page = chrome.page(title, back);
        if (rows.length() == 0) page.addView(ui.empty(empty));
        NativeRecordViews records = new NativeRecordViews(ui);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            LinearLayout card = records.card(row);
            card.setClickable(true);
            card.setOnClickListener(view -> new NativeRecordDetailScreen(
                    ui, host, title, row, this::show).show());
            card.addView(ui.label("查看详情  ›", 13, UiKit.BRAND, true));
            page.addView(card);
        }
        host.show(ui.scroll(page));
    }
}
