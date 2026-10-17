package com.gmc1987.chronos.mobile.feature.applications;

import android.widget.Button;
import android.widget.LinearLayout;
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
            card.addView(ui.label(application.optString("name", "应用"), 17, UiKit.TEXT, true));
            card.addView(ui.label(application.optString("description", "已由服务端授权"),
                    13, UiKit.MUTED, false));
            Button open = ui.secondaryButton("打开并办理");
            open.setEnabled(!application.optString("routePath", "").isEmpty());
            open.setOnClickListener(view -> routes.open(application));
            card.addView(open);
            page.addView(card);
        }
        host.show(ui.scroll(page));
    }

    public interface RouteHandler { void open(JSONObject application); }
}
