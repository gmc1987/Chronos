package com.gmc1987.chronos.mobile.feature.family;

import android.widget.Button;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ErrorMessages;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import org.json.JSONArray;
import org.json.JSONObject;

public final class FamilyNoticesScreen {
    private final android.app.Activity activity;
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final FamilyRepository repository;
    private final Runnable back;

    public FamilyNoticesScreen(android.app.Activity activity, UiKit ui, AsyncRunner async, ScreenHost host,
            FamilyRepository repository, Runnable back) {
        this.activity = activity; this.ui = ui; this.async = async; this.host = host;
        this.repository = repository; this.back = back;
    }

    public void show() {
        host.setBackAction(back);
        LinearLayout page = ui.page("家校通知");
        LinearLayout body = ui.vertical();
        body.addView(ui.centered("正在加载…", true));
        page.addView(body);
        host.show(ui.scroll(page));
        async.run(repository::notices, response -> render(body, response), error -> {
            body.removeAllViews();
            body.addView(ui.error(ErrorMessages.display(error, "家校通知加载失败"), this::show));
        });
    }

    private void render(LinearLayout body, JSONObject response) {
        body.removeAllViews();
        JSONArray values = response.optJSONArray("data");
        if (values == null || values.length() == 0) { body.addView(ui.empty("当前没有家校通知")); return; }
        for (int i = 0; i < values.length(); i++) {
            JSONObject notice = values.optJSONObject(i);
            if (notice == null) continue;
            LinearLayout card = ui.card();
            card.addView(ui.label(notice.optString("title", "家校通知"), 17, UiKit.TEXT, true));
            card.addView(ui.label(notice.optString("content", "暂无内容"), 14, UiKit.TEXT, false));
            card.addView(ui.label(notice.optString("publishAt", ""), 12, UiKit.MUTED, false));
            if (notice.optBoolean("receiptRequired")) {
                if ("RECEIVED".equals(notice.optString("receiptStatus"))) {
                    card.addView(ui.label("已回执", 13, UiKit.BRAND, true));
                } else if (notice.optBoolean("expired")) {
                    card.addView(ui.label("已过期，无法回执", 13, UiKit.MUTED, false));
                } else {
                    Button receipt = ui.primaryButton("确认收到");
                    receipt.setOnClickListener(view -> receipt(notice.optString("id")));
                    card.addView(receipt);
                }
            }
            body.addView(card);
        }
    }

    private void receipt(String id) {
        async.run(() -> repository.receipt(id), ignored -> show(),
                error -> android.widget.Toast.makeText(
                        activity,
                        ErrorMessages.display(error, "回执失败"),
                        android.widget.Toast.LENGTH_LONG).show());
    }
}
