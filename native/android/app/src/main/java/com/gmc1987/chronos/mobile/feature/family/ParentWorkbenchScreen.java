package com.gmc1987.chronos.mobile.feature.family;

import android.widget.Button;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ErrorMessages;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.model.PortalState;
import org.json.JSONArray;
import org.json.JSONObject;

/** Parent landing page backed by the verified parent-profile binding chain. */
public final class ParentWorkbenchScreen {
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final PortalState portal;
    private final FamilyRepository repository;
    private final Runnable notices;
    private final Runnable feedback;

    public ParentWorkbenchScreen(UiKit ui, AsyncRunner async, ScreenHost host, PortalState portal,
            FamilyRepository repository, Runnable notices, Runnable feedback) {
        this.ui = ui; this.async = async; this.host = host; this.portal = portal;
        this.repository = repository; this.notices = notices; this.feedback = feedback;
    }

    public void show() {
        LinearLayout page = ui.page("家长工作台");
        LinearLayout identity = ui.card();
        identity.addView(ui.label("您好，" + portal.displayName(), 22, UiKit.TEXT, true));
        identity.addView(ui.label("子女学业与家校协同", 14, UiKit.MUTED, false));
        page.addView(identity);
        LinearLayout children = loadingCard(page, "我的子女");
        LinearLayout grades = loadingCard(page, "成绩概览");
        Button noticeButton = ui.primaryButton("家校通知与回执");
        noticeButton.setOnClickListener(view -> notices.run());
        page.addView(noticeButton);
        Button feedbackButton = ui.secondaryButton("提交家长反馈");
        feedbackButton.setOnClickListener(view -> feedback.run());
        page.addView(feedbackButton);
        host.show(ui.scroll(page));
        async.run(repository::children, response -> renderChildren(children, response),
                error -> renderError(children, error, this::show));
        async.run(repository::grades, response -> renderGrades(grades, response),
                error -> renderError(grades, error, this::show));
    }

    private LinearLayout loadingCard(LinearLayout page, String title) {
        LinearLayout card = ui.card();
        card.addView(ui.label(title, 18, UiKit.TEXT, true));
        LinearLayout body = ui.vertical();
        body.addView(ui.centered("正在加载…", true));
        card.addView(body);
        page.addView(card);
        return body;
    }

    private void renderChildren(LinearLayout body, JSONObject response) {
        body.removeAllViews();
        JSONArray values = response.optJSONArray("data");
        if (values == null || values.length() == 0) { body.addView(ui.empty("当前账号尚未绑定有效学生")); return; }
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.optJSONObject(i);
            if (value == null) continue;
            body.addView(ui.label(value.optString("studentName", "学生") + " · "
                    + value.optString("studentNo") + " · " + value.optString("relationship"),
                    14, UiKit.TEXT, false));
        }
    }

    private void renderGrades(LinearLayout body, JSONObject response) {
        body.removeAllViews();
        JSONArray values = response.optJSONArray("data");
        if (values == null || values.length() == 0) { body.addView(ui.empty("当前没有已发布成绩")); return; }
        for (int i = 0; i < Math.min(5, values.length()); i++) {
            JSONObject value = values.optJSONObject(i);
            if (value == null) continue;
            String course = value.optString("courseName", value.optString("course", "课程"));
            String score = value.optString("score", value.optString("finalScore", "待发布"));
            body.addView(ui.label(course + " · " + score, 14, UiKit.TEXT, false));
        }
    }

    private void renderError(LinearLayout body, Exception error, Runnable retry) {
        body.removeAllViews();
        body.addView(ui.error(ErrorMessages.display(error, "加载失败"), retry));
    }
}
