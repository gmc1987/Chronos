package com.gmc1987.chronos.mobile.core.common;

import android.view.View;
import android.widget.LinearLayout;
import java.util.List;
import org.json.JSONObject;

public final class RemoteListScreen {
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;

    public RemoteListScreen(UiKit ui, AsyncRunner async, ScreenHost host) {
        this.ui = ui;
        this.async = async;
        this.host = host;
    }

    public void show(
            String title, String emptyText, Loader loader, Formatter formatter) {
        LinearLayout page = ui.page(title);
        LinearLayout body = ui.vertical();
        body.addView(ui.centered("正在加载…", true));
        page.addView(body);
        host.show(ui.scroll(page));
        async.run(loader::load, response -> {
            body.removeAllViews();
            List<String> rows = formatter.rows(response.opt("data"));
            if (rows.isEmpty()) {
                body.addView(ui.empty(emptyText));
                return;
            }
            for (String row : rows) {
                LinearLayout card = ui.card();
                card.addView(ui.label(row, 15, UiKit.TEXT, false));
                body.addView(card);
            }
        }, error -> {
            body.removeAllViews();
            body.addView(ui.error(ErrorMessages.display(error, title + "加载失败"),
                    () -> show(title, emptyText, loader, formatter)));
        });
    }

    public interface Loader { JSONObject load() throws Exception; }
    public interface Formatter { List<String> rows(Object data); }
}
