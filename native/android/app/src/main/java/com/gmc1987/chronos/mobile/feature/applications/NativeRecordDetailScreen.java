package com.gmc1987.chronos.mobile.feature.applications;

import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import org.json.JSONObject;

/** Native detail page for application records without a specialized editor. */
public final class NativeRecordDetailScreen {
    private final UiKit ui;
    private final ScreenHost host;
    private final String title;
    private final JSONObject record;
    private final Runnable back;

    public NativeRecordDetailScreen(UiKit ui, ScreenHost host, String title,
            JSONObject record, Runnable back) {
        this.ui = ui;
        this.host = host;
        this.title = title;
        this.record = record;
        this.back = back;
    }

    public void show() {
        ApplicationScreenChrome chrome = new ApplicationScreenChrome(ui, host);
        LinearLayout page = chrome.page(title + "详情", back);
        new NativeRecordViews(ui).appendDetail(page, record, "");
        host.show(ui.scroll(page));
    }
}
