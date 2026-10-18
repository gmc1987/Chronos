package com.gmc1987.chronos.mobile.feature.applications;

import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.feature.workbench.MeetingDetailScreen;
import com.gmc1987.chronos.mobile.feature.workbench.MeetingsRepository;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native meeting list that opens the actionable meeting detail screen. */
public final class MeetingsScreen {
    private final UiKit ui; private final AsyncRunner async; private final ScreenHost host;
    private final MeetingsRepository meetings; private final Runnable back;

    public MeetingsScreen(UiKit ui, AsyncRunner async, ScreenHost host,
            MeetingsRepository meetings, Runnable back) {
        this.ui = ui; this.async = async; this.host = host;
        this.meetings = meetings; this.back = back;
    }

    public void show() {
        ApplicationScreenChrome chrome = new ApplicationScreenChrome(ui, host);
        chrome.loading("正在加载会议…");
        async.run(meetings::list, response -> render(response.optJSONArray("data")),
                error -> chrome.error("我的会议", error, this::show, back));
    }

    private void render(JSONArray rows) {
        LinearLayout page = new ApplicationScreenChrome(ui, host).page("我的会议", back);
        if (rows == null || rows.length() == 0) page.addView(ui.empty("暂无会议"));
        else {
            NativeRecordViews records = new NativeRecordViews(ui);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i); if (row == null) continue;
                LinearLayout card = records.card(row);
                card.addView(ui.label("查看详情与参会确认  ›", 13, UiKit.BRAND, true));
                card.setClickable(true);
                card.setOnClickListener(view -> new MeetingDetailScreen(
                        ui, async, host, meetings, row, "返回会议", this::show).show());
                page.addView(card);
            }
        }
        host.show(ui.scroll(page));
    }
}
