package com.gmc1987.chronos.mobile.feature.workbench;

import android.widget.Button;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import java.util.Iterator;
import org.json.JSONObject;

/** Native detail for workbench summary records that do not have a dedicated business screen. */
public final class WorkbenchItemDetailScreen {
    private final UiKit ui;
    private final ScreenHost host;
    private final String title;
    private final JSONObject item;
    private final Runnable back;

    public WorkbenchItemDetailScreen(UiKit ui, ScreenHost host, String title,
            JSONObject item, Runnable back) {
        this.ui = ui;
        this.host = host;
        this.title = title;
        this.item = item;
        this.back = back;
    }

    public void show() {
        LinearLayout page = ui.page(title + "详情");
        Button button = ui.secondaryButton("‹ 返回工作台");
        button.setOnClickListener(view -> back.run());
        page.addView(button);
        Iterator<String> keys = item.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = item.opt(key);
            if (value == null || value == JSONObject.NULL || value instanceof JSONObject) continue;
            LinearLayout card = ui.card();
            card.addView(ui.label(key, 13, UiKit.MUTED, false));
            card.addView(ui.label(String.valueOf(value), 15, UiKit.TEXT, false));
            page.addView(card);
        }
        host.show(ui.scroll(page));
    }
}
