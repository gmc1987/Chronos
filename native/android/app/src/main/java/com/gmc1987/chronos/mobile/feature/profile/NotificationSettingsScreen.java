package com.gmc1987.chronos.mobile.feature.profile;

import android.content.Intent;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;

/** Opens the OS notification controls so the displayed state is always authoritative. */
public final class NotificationSettingsScreen {
    private final UiKit ui;
    private final ScreenHost host;
    private final Runnable back;

    public NotificationSettingsScreen(UiKit ui, ScreenHost host, Runnable back) {
        this.ui = ui;
        this.host = host;
        this.back = back;
    }

    public void show() {
        host.setBackAction(back);
        LinearLayout page = ui.page("消息设置");
        LinearLayout card = ui.card();
        card.addView(ui.label("系统通知权限", 18, UiKit.TEXT, true));
        card.addView(ui.label("通知声音、横幅、锁屏显示和免打扰规则由 Android 统一管理。",
                14, UiKit.MUTED, false));
        Button open = ui.primaryButton("打开系统通知设置");
        open.setOnClickListener(view -> {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, view.getContext().getPackageName());
            view.getContext().startActivity(intent);
        });
        card.addView(open);
        page.addView(card);
        host.show(ui.scroll(page));
    }
}
