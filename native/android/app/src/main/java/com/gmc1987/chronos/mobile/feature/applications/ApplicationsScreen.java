package com.gmc1987.chronos.mobile.feature.applications;

import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.model.PortalState;
import org.json.JSONObject;

public final class ApplicationsScreen {
    private final UiKit ui;
    private final ScreenHost host;
    private final PortalState portal;
    private final RouteHandler routes;

    public ApplicationsScreen(UiKit ui, ScreenHost host, PortalState portal, RouteHandler routes) {
        this.ui = ui;
        this.host = host;
        this.portal = portal;
        this.routes = routes;
    }

    public void show() {
        LinearLayout page = ui.page("应用");
        if (portal.applications().length() == 0) {
            page.addView(ui.empty("当前账号暂无授权应用"));
        }
        for (int i = 0; i < portal.applications().length(); i++) {
            JSONObject application = portal.applications().optJSONObject(i);
            LinearLayout card = ui.card();
            LinearLayout row = ui.horizontal();
            String name = application.optString("name", "应用");
            if (name.isEmpty()) name = "应用";
            row.addView(ui.badge(name.substring(0, 1), 42));
            LinearLayout copy = ui.vertical();
            copy.setPadding(ui.dp(13), 0, ui.dp(12), 0);
            copy.addView(ui.label(name, 17, UiKit.TEXT, true));
            TextView detail = ui.label(
                    application.optString("description", "服务端授权应用"),
                    13,
                    UiKit.MUTED,
                    false);
            detail.setMaxLines(2);
            copy.addView(detail);
            row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            Button open = ui.secondaryButton("打开  ›");
            open.setEnabled(!application.optString("routePath", "").isEmpty());
            open.setOnClickListener(view -> routes.open(application));
            row.addView(open, new LinearLayout.LayoutParams(ui.dp(86), ui.dp(44)));
            card.addView(row);
            page.addView(card);
        }
        host.show(ui.scroll(page));
    }

    public interface RouteHandler { void open(JSONObject application); }
}
