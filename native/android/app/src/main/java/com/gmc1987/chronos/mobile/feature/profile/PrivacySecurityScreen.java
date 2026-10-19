package com.gmc1987.chronos.mobile.feature.profile;

import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;

/** Describes the local security boundary and provides a safe cache cleanup action. */
public final class PrivacySecurityScreen {
    private final UiKit ui;
    private final ScreenHost host;
    private final HttpClient http;
    private final Runnable back;

    public PrivacySecurityScreen(UiKit ui, ScreenHost host, HttpClient http, Runnable back) {
        this.ui = ui;
        this.host = host;
        this.http = http;
        this.back = back;
    }

    public void show() {
        host.setBackAction(back);
        LinearLayout page = ui.page("隐私与安全");
        LinearLayout card = ui.card();
        card.addView(ui.label("登录凭证", 18, UiKit.TEXT, true));
        card.addView(ui.label("登录令牌使用 Android Keystore 加密保存，退出登录时会清除。",
                14, UiKit.MUTED, false));
        card.addView(ui.divider());
        card.addView(ui.label("离线数据", 18, UiKit.TEXT, true));
        card.addView(ui.label("只缓存允许离线查看的只读接口，缓存最多保留 24 小时。",
                14, UiKit.MUTED, false));
        Button clear = ui.secondaryButton("清除离线缓存");
        clear.setOnClickListener(view -> {
            http.clearCache();
            Toast.makeText(view.getContext(), "离线缓存已清除",
                    Toast.LENGTH_LONG).show();
        });
        card.addView(clear);
        page.addView(card);
        host.show(ui.scroll(page));
    }
}
