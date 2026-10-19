package com.gmc1987.chronos.mobile.feature.profile;

import android.widget.Button;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.model.PortalState;
import com.gmc1987.chronos.mobile.feature.auth.AuthRepository;
import com.gmc1987.chronos.mobile.core.network.HttpClient;

public final class ProfileScreen {
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final PortalState portal;
    private final AuthRepository auth;
    private final HttpClient http;
    private final Runnable loggedOut;

    public ProfileScreen(
            UiKit ui,
            AsyncRunner async,
            ScreenHost host,
            PortalState portal,
            AuthRepository auth,
            HttpClient http,
            Runnable loggedOut) {
        this.ui = ui;
        this.async = async;
        this.host = host;
        this.portal = portal;
        this.auth = auth;
        this.http = http;
        this.loggedOut = loggedOut;
    }

    public void show() {
        LinearLayout page = ui.page("我的");
        LinearLayout card = ui.card();
        card.addView(ui.label(portal.displayName(), 22, UiKit.TEXT, true));
        card.addView(ui.label(workspaceLabel(), 14, UiKit.BRAND, true));
        card.addView(ui.label("账号：" + portal.user().optString("username", "—"),
                13, UiKit.MUTED, false));
        if (!portal.organization().isEmpty()) {
            card.addView(ui.label("组织：" + portal.organization(), 13, UiKit.MUTED, false));
        }
        String employeeCode = portal.user().optString("employeeCode", "");
        if (!employeeCode.isEmpty()) {
            card.addView(ui.label("工号：" + employeeCode, 13, UiKit.MUTED, false));
        }
        page.addView(card);
        LinearLayout settings = ui.card();
        Button notifications = ui.secondaryButton("消息设置");
        notifications.setOnClickListener(view -> new NotificationSettingsScreen(
                ui, host, this::show).show());
        settings.addView(notifications);
        settings.addView(ui.divider());
        Button privacy = ui.secondaryButton("隐私与安全");
        privacy.setOnClickListener(view -> new PrivacySecurityScreen(
                ui, host, http, this::show).show());
        settings.addView(privacy);
        settings.addView(ui.divider());
        settings.addView(ui.label("Chronos Android 0.1.0", 13, UiKit.MUTED, false));
        page.addView(settings);
        Button logout = ui.destructiveButton("退出登录");
        logout.setOnClickListener(view -> {
            logout.setEnabled(false);
            logout.setText("正在退出…");
            async.run(() -> {
                auth.logout();
                return true;
            }, ignored -> loggedOut.run(), error -> loggedOut.run());
        });
        page.addView(logout);
        host.show(ui.scroll(page));
    }

    private String workspaceLabel() {
        switch (portal.workspace()) {
            case STUDENT: return "学生";
            case TEACHER: return "教师";
            case MANAGEMENT: return "校领导 / 管理人员";
            case PARENT: return "家长";
            default: return "教职工";
        }
    }
}
