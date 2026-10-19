package com.gmc1987.chronos.mobile.feature.announcements;

import android.app.Activity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ErrorMessages;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Searchable, incrementally paged publication list with native detail navigation. */
public final class PublicationsScreen {
    private final Activity activity;
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final AnnouncementsRepository repository;
    private final String title;
    private final String type;
    private final String empty;
    private final Runnable back;
    private JSONArray rows = new JSONArray();
    private LinearLayout body;
    private EditText keyword;
    private Switch unread;
    private int page;
    private boolean last = true;
    private boolean loading;

    public PublicationsScreen(Activity activity, UiKit ui, AsyncRunner async, ScreenHost host,
            AnnouncementsRepository repository, String title, String type, String empty, Runnable back) {
        this.activity = activity;
        this.ui = ui;
        this.async = async;
        this.host = host;
        this.repository = repository;
        this.title = title;
        this.type = type;
        this.empty = empty;
        this.back = back;
    }

    public void show() {
        host.setBackAction(back);
        LinearLayout root = ui.page(title);
        keyword = ui.input("搜索标题或内容", false);
        root.addView(keyword);
        LinearLayout tools = ui.horizontal();
        unread = new Switch(activity);
        unread.setText("只看未读");
        tools.addView(unread, new LinearLayout.LayoutParams(0, -2, 1));
        Button search = ui.secondaryButton("查询");
        search.setOnClickListener(view -> reload());
        tools.addView(search);
        root.addView(tools);
        body = ui.vertical();
        root.addView(body);
        host.show(ui.scroll(root));
        reload();
    }

    private void reload() {
        if (loading) return;
        rows = new JSONArray();
        page = 0;
        load(true);
    }

    private void load(boolean reset) {
        if (loading) return;
        loading = true;
        if (reset) {
            body.removeAllViews();
            body.addView(ui.centered("正在加载…", true));
        }
        async.run(() -> repository.list(type, keyword.getText().toString(), unread.isChecked(), page),
                response -> {
                    loading = false;
                    JSONObject data = response.optJSONObject("data");
                    JSONArray content = data == null ? null : data.optJSONArray("content");
                    if (content != null) for (int i = 0; i < content.length(); i++) rows.put(content.opt(i));
                    last = data == null || data.optBoolean("last", true);
                    if (!last) page++;
                    render();
                }, error -> {
                    loading = false;
                    body.removeAllViews();
                    body.addView(ui.error(ErrorMessages.display(error, title + "加载失败"), this::reload));
                });
    }

    private void render() {
        body.removeAllViews();
        if (rows.length() == 0) { body.addView(ui.empty(empty)); return; }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject publication = rows.optJSONObject(i);
            if (publication == null) continue;
            LinearLayout card = ui.card();
            String unreadMark = publication.optBoolean("read") ? "" : "  · 未读";
            card.addView(ui.label(publication.optString("title", "未命名通知") + unreadMark,
                    17, UiKit.TEXT, true));
            card.addView(ui.label(publication.optString("summary", "暂无摘要"), 14, UiKit.MUTED, false));
            card.setOnClickListener(view -> new PublicationDetailScreen(activity, ui, async, host,
                    repository, publication.optString("id"), this::show).show());
            body.addView(card);
        }
        if (!last) {
            Button more = ui.secondaryButton("加载更多");
            more.setOnClickListener(view -> load(false));
            body.addView(more);
        }
    }
}
