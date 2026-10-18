package com.gmc1987.chronos.mobile.feature.applications;

import android.app.Activity;
import android.widget.Button;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;

/** Native workflow center category screen. */
public final class WorkflowCenterScreen {
    private final Activity activity;
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final HttpClient http;
    private final Runnable back;

    public WorkflowCenterScreen(Activity activity, UiKit ui, AsyncRunner async, ScreenHost host,
            HttpClient http, Runnable back) {
        this.activity = activity; this.ui = ui; this.async = async;
        this.host = host; this.http = http; this.back = back;
    }

    public void show() {
        LinearLayout page = new ApplicationScreenChrome(ui, host).page("流程中心", back);
        add(page, "待办", "待办任务", "/workflow-tasks/pending?page=0&size=50", "暂无待办任务");
        add(page, "已办", "已办任务", "/workflow-tasks/handled?page=0&size=50", "暂无已办任务");
        add(page, "我发起", "我发起的", "/workflow-instances/initiated?page=0&size=50", "暂无发起记录");
        LinearLayout start = ui.card();
        start.addView(ui.label("发起流程", 18, UiKit.TEXT, true));
        start.addView(ui.label("选择已发布流程并填写原生动态表单", 13, UiKit.MUTED, false));
        start.addView(ui.label("打开  ›", 13, UiKit.BRAND, true));
        start.setClickable(true);
        start.setOnClickListener(view -> new AvailableWorkflowsScreen(
                activity, ui, async, host, http, this::show).show());
        page.addView(start);
        host.show(ui.scroll(page));
    }

    public void showInstance(String instanceId) {
        new WorkflowInstanceScreen(activity, ui, async, host, http, instanceId, back).show();
    }

    private void add(LinearLayout page, String label, String title, String path, String empty) {
        LinearLayout card = ui.card();
        card.addView(ui.label(label, 18, UiKit.TEXT, true));
        card.addView(ui.label(empty, 13, UiKit.MUTED, false));
        card.addView(ui.label("打开  ›", 13, UiKit.BRAND, true));
        card.setClickable(true);
        card.setOnClickListener(view -> new WorkflowRecordsScreen(
                activity, ui, async, host, http, title, path, empty, this::show).show());
        page.addView(card);
    }
}
