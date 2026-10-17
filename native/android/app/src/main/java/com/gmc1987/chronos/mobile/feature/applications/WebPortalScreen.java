package com.gmc1987.chronos.mobile.feature.applications;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.BuildConfig;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.SessionStore;
import org.json.JSONObject;

/** Opens the complete portal feature while keeping the native session and navigation shell. */
public final class WebPortalScreen {
    private final Activity activity;
    private final UiKit ui;
    private final ScreenHost host;
    private final SessionStore session;
    private final JSONObject application;
    private final Runnable back;

    public WebPortalScreen(Activity activity, UiKit ui, ScreenHost host, SessionStore session,
            JSONObject application, Runnable back) {
        this.activity = activity;
        this.ui = ui;
        this.host = host;
        this.session = session;
        this.application = application;
        this.back = back;
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void show() {
        String route = application.optString("routePath", "");
        if ("EXTERNAL".equalsIgnoreCase(application.optString("openMode"))
                || route.startsWith("https://") || route.startsWith("http://")) {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(route)));
            back.run();
            return;
        }
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true);
        LinearLayout page = ui.vertical();
        page.setBackgroundColor(Color.WHITE);
        LinearLayout bar = ui.horizontal();
        bar.setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(6));
        Button backButton = ui.secondaryButton("返回应用中心");
        backButton.setOnClickListener(view -> back.run());
        bar.addView(backButton);
        bar.addView(ui.label(application.optString("name", "应用"), 17, UiKit.TEXT, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(bar);

        WebView web = new WebView(activity);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setLoadWithOverviewMode(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        page.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));

        if (route.isEmpty()) route = "/portal/apps";
        String target = BuildConfig.PORTAL_BASE_URL + (route.startsWith("/") ? route : "/" + route);
        String script = "localStorage.setItem('admin_access_token',"
                + JSONObject.quote(session.accessToken()) + ");location.replace("
                + JSONObject.quote(target) + ");";
        String html = "<!doctype html><html><body><script>" + script + "</script></body></html>";
        web.loadDataWithBaseURL(BuildConfig.PORTAL_BASE_URL, html, "text/html", "UTF-8", null);
        host.show(page);
    }
}
