package com.gmc1987.chronos.mobile.feature.applications;

import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ErrorMessages;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.model.PortalState;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Search, favorites and recent visits for server-authorized native applications. */
public final class ApplicationsScreen {
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final PortalState portal;
    private final HttpClient http;
    private final RouteHandler routes;
    private final Set<String> favoriteIds = new HashSet<>();
    private JSONArray recent = new JSONArray();
    private LinearLayout body;
    private EditText query;

    public ApplicationsScreen(UiKit ui, AsyncRunner async, ScreenHost host, PortalState portal,
            HttpClient http, RouteHandler routes) {
        this.ui = ui;
        this.async = async;
        this.host = host;
        this.portal = portal;
        this.http = http;
        this.routes = routes;
    }

    public void show() {
        LinearLayout page = ui.page("应用");
        query = ui.input("搜索应用", false);
        page.addView(query);
        Button search = ui.secondaryButton("查询");
        search.setOnClickListener(view -> render());
        page.addView(search);
        body = ui.vertical();
        page.addView(body);
        host.show(ui.scroll(page));
        loadPersonalization();
    }

    private void loadPersonalization() {
        body.removeAllViews();
        body.addView(ui.centered("正在加载应用…", true));
        async.run(() -> {
            JSONObject favorites = http.get("/portal/favorites");
            JSONObject recentResponse = http.get("/portal/recent-visits");
            return new JSONObject().put("favorites", favorites.optJSONArray("data"))
                    .put("recent", recentResponse.optJSONArray("data"));
        }, result -> {
            favoriteIds.clear();
            JSONArray favorites = result.optJSONArray("favorites");
            if (favorites != null) {
                for (int i = 0; i < favorites.length(); i++) {
                    JSONObject value = favorites.optJSONObject(i);
                    if (value != null) favoriteIds.add(value.optString("id"));
                }
            }
            recent = result.optJSONArray("recent") == null
                    ? new JSONArray() : result.optJSONArray("recent");
            render();
        }, error -> {
            body.removeAllViews();
            body.addView(ui.error(
                    ErrorMessages.display(error, "应用加载失败"),
                    this::loadPersonalization));
        });
    }

    private void render() {
        body.removeAllViews();
        String keyword = query.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (keyword.isEmpty() && recent.length() > 0) {
            addSection("最近使用", recent, keyword, false);
        }
        addSection("我的收藏", portal.applications(), keyword, true);
        addSection(keyword.isEmpty() ? "全部应用" : "搜索结果",
                portal.applications(), keyword, false);
        if (body.getChildCount() == 0) body.addView(ui.empty("没有匹配的应用"));
    }

    private void addSection(
            String title, JSONArray values, String keyword, boolean favoritesOnly) {
        LinearLayout section = ui.vertical();
        int count = 0;
        for (int i = 0; i < values.length(); i++) {
            JSONObject application = values.optJSONObject(i);
            if (application == null || !matches(application, keyword)) continue;
            boolean favorite = favoriteIds.contains(application.optString("id"));
            if (!"最近使用".equals(title) && favoritesOnly != favorite) continue;
            section.addView(applicationCard(application, favorite));
            count++;
        }
        if (count == 0) return;
        body.addView(ui.label(title, 18, UiKit.TEXT, true));
        body.addView(section);
    }

    private LinearLayout applicationCard(JSONObject application, boolean favorite) {
        LinearLayout card = ui.card();
        LinearLayout row = ui.horizontal();
        String name = application.optString("name", "应用");
        if (name.isEmpty()) name = "应用";
        row.addView(ui.badge(name.substring(0, 1), 42));
        LinearLayout copy = ui.vertical();
        copy.setPadding(ui.dp(13), 0, ui.dp(8), 0);
        copy.addView(ui.label(name, 17, UiKit.TEXT, true));
        TextView detail = ui.label(
                application.optString("description", "服务端授权应用"),
                13,
                UiKit.MUTED,
                false);
        detail.setMaxLines(2);
        copy.addView(detail);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        Button star = ui.secondaryButton(favorite ? "★" : "☆");
        star.setContentDescription(favorite ? "取消收藏" : "收藏");
        star.setOnClickListener(view -> toggleFavorite(application, favorite));
        row.addView(star, new LinearLayout.LayoutParams(ui.dp(52), ui.dp(44)));
        card.addView(row);
        card.setOnClickListener(view -> open(application));
        return card;
    }

    private void toggleFavorite(JSONObject application, boolean favorite) {
        String id = application.optString("id");
        async.run(() -> favorite
                        ? http.delete("/portal/favorites/" + id)
                        : http.put("/portal/favorites/" + id, new JSONObject()),
                ignored -> {
                    if (favorite) favoriteIds.remove(id);
                    else favoriteIds.add(id);
                    render();
                },
                error -> android.widget.Toast.makeText(
                        query.getContext(),
                        ErrorMessages.display(error, "收藏操作失败"),
                        android.widget.Toast.LENGTH_LONG).show());
    }

    private void open(JSONObject application) {
        String id = application.optString("id");
        async.run(
                () -> http.post(
                        "/portal/applications/" + id + "/visit",
                        new JSONObject(),
                        true),
                ignored -> {},
                error -> {});
        routes.open(application);
    }

    private boolean matches(JSONObject value, String keyword) {
        if (keyword.isEmpty()) return true;
        return (value.optString("name") + " " + value.optString("description"))
                .toLowerCase(Locale.ROOT)
                .contains(keyword);
    }

    public interface RouteHandler {
        void open(JSONObject application);
    }
}
