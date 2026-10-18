package com.gmc1987.chronos.mobile.feature.applications;

import android.app.Activity;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import java.util.List;

/** Native application submodule hub using the same rounded-card hierarchy as iOS. */
public final class NativeModuleHubScreen {
    private final Activity activity;
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final HttpClient http;
    private final String title;
    private final List<NativeModule> modules;
    private final Runnable back;

    public NativeModuleHubScreen(Activity activity, UiKit ui, AsyncRunner async, ScreenHost host,
            HttpClient http, String title, List<NativeModule> modules, Runnable back) {
        this.activity = activity;
        this.ui = ui;
        this.async = async;
        this.host = host;
        this.http = http;
        this.title = title;
        this.modules = modules;
        this.back = back;
    }

    public void show() {
        LinearLayout page = new ApplicationScreenChrome(ui, host).page(title, back);
        for (NativeModule module : modules) {
            LinearLayout card = ui.card();
            card.addView(ui.label(module.title, 18, UiKit.TEXT, true));
            card.addView(ui.label(module.detail, 13, UiKit.MUTED, false));
            card.addView(ui.label("打开  ›", 13, UiKit.BRAND, true));
            card.setClickable(true);
            card.setOnClickListener(view -> open(module));
            page.addView(card);
        }
        host.show(ui.scroll(page));
    }

    private void open(NativeModule module) {
        switch (module.kind) {
            case NativeModule.LEAVES:
                new LeaveRequestsScreen(activity, ui, async, host, http, this::show).show();
                break;
            case NativeModule.OFFICE_REQUESTS:
                new OfficeRequestsScreen(activity, ui, async, host, http, this::show).show();
                break;
            case NativeModule.BUSINESS_TRIPS:
                new BusinessTripsScreen(activity, ui, async, host, http, this::show).show();
                break;
            default:
                new NativeRecordsScreen(ui, async, host, http, module.title, module.path,
                        "暂无" + module.title + "数据", this::show).show();
                break;
        }
    }
}
