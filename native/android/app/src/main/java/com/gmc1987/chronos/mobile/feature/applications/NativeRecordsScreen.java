package com.gmc1987.chronos.mobile.feature.applications;

import android.widget.LinearLayout;
import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;
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
        if (rows.length() == 0) {
            page.addView(ui.empty(empty));
            host.show(ui.scroll(page));
            return;
        }
        EditText search = ui.input("搜索当前列表", false);
        LinearLayout results = ui.vertical();
        page.addView(search);
        page.addView(results);
        renderRows(rows, "", results);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderRows(rows, s.toString(), results);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        host.show(ui.scroll(page));
    }

    private void renderRows(JSONArray rows, String keyword, LinearLayout container) {
        container.removeAllViews();
        NativeRecordViews records = new NativeRecordViews(ui);
        int visible = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            if (!keyword.trim().isEmpty()
                    && !row.toString().toLowerCase(java.util.Locale.ROOT)
                            .contains(keyword.trim().toLowerCase(java.util.Locale.ROOT))) continue;
            LinearLayout card = records.card(row);
            card.setClickable(true);
            card.setOnClickListener(view -> new NativeRecordDetailScreen(
                    ui, host, title, row, this::show).show());
            card.addView(ui.label("查看详情  ›", 13, UiKit.BRAND, true));
            container.addView(card);
            visible++;
        }
        if (visible == 0) container.addView(ui.empty("没有匹配记录"));
    }
}
