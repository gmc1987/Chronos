package com.gmc1987.chronos.mobile.feature.applications;

import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.Button;
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
    private JSONArray rows = new JSONArray();
    private LinearLayout results;
    private EditText search;
    private int page;
    private boolean last = true;

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
        page = 0;
        rows = new JSONArray();
        async.run(() -> http.get(pagedPath(page)), response -> render(response, true),
                error -> chrome.error(title, error, this::show, back));
    }

    private void render(JSONObject response, boolean reset) {
        JSONArray loaded = ApplicationJson.rows(response);
        if (reset) rows = new JSONArray();
        for (int i = 0; i < loaded.length(); i++) rows.put(loaded.opt(i));
        JSONObject data = response.optJSONObject("data");
        last = !supportsPaging() || data == null || data.optBoolean("last", true);
        if (!last) page++;
        ApplicationScreenChrome chrome = new ApplicationScreenChrome(ui, host);
        LinearLayout page = chrome.page(title, back);
        if (rows.length() == 0) {
            page.addView(ui.empty(empty));
            host.show(ui.scroll(page));
            return;
        }
        search = ui.input("搜索当前列表", false);
        results = ui.vertical();
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
        if (!last && keyword.trim().isEmpty()) {
            Button more = ui.secondaryButton("加载更多");
            more.setOnClickListener(view -> loadMore());
            container.addView(more);
        }
    }

    private void loadMore() {
        async.run(() -> http.get(pagedPath(page)), response -> render(response, false),
                error -> android.widget.Toast.makeText(search.getContext(), error.getMessage(),
                        android.widget.Toast.LENGTH_LONG).show());
    }

    private boolean supportsPaging() { return path.contains("page="); }

    private String pagedPath(int targetPage) {
        if (!supportsPaging()) return path;
        return path.replaceAll("([?&]page=)\\d+", "$1" + targetPage);
    }
}
