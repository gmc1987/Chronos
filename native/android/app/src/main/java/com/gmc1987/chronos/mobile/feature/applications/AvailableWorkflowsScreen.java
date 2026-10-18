package com.gmc1987.chronos.mobile.feature.applications;

import android.app.Activity;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Native published workflow selector. */
public final class AvailableWorkflowsScreen {
    /** These flows must create their domain record through the matching native business screen. */
    private static final Set<String> DOMAIN_MANAGED_FLOWS = new HashSet<>(Arrays.asList(
            "EDU_GRADEBOOK_REVIEW", "EDU_GRADE_CHANGE_REVIEW", "EDU_TEACHING_CONTENT_REVIEW",
            "EDU_CLASSROOM_RESERVATION_APPROVAL", "STAFF_BUSINESS_TRIP_APPROVAL",
            "STAFF_SEAL_USE_APPROVAL", "STAFF_VEHICLE_USE_APPROVAL",
            "EDU_TEACHER_LEAVE_APPROVAL", "EDU_COURSE_ADJUSTMENT_APPROVAL"));
    private final Activity activity; private final UiKit ui; private final AsyncRunner async;
    private final ScreenHost host; private final HttpClient http; private final Runnable back;

    public AvailableWorkflowsScreen(Activity activity, UiKit ui, AsyncRunner async, ScreenHost host,
            HttpClient http, Runnable back) {
        this.activity = activity; this.ui = ui; this.async = async;
        this.host = host; this.http = http; this.back = back;
    }

    public void show() {
        ApplicationScreenChrome chrome = new ApplicationScreenChrome(ui, host);
        chrome.loading("正在加载可发起流程…");
        async.run(() -> ApplicationJson.rows(http.get("/workflows/available")), this::render,
                error -> chrome.error("发起流程", error, this::show, back));
    }

    private void render(JSONArray rows) {
        LinearLayout page = new ApplicationScreenChrome(ui, host).page("发起流程", back);
        if (rows.length() == 0) page.addView(ui.empty("暂无可发起流程"));
        NativeRecordViews records = new NativeRecordViews(ui);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i); if (row == null) continue;
            if (DOMAIN_MANAGED_FLOWS.contains(row.optString("flowCode"))) continue;
            String flowId = row.optString("id");
            LinearLayout card = records.card(row);
            if (!flowId.isEmpty()) {
                card.setClickable(true);
                card.setOnClickListener(view -> new WorkflowStartScreen(
                        activity, ui, async, host, http, flowId, this::show).show());
                card.addView(ui.label("填写申请  ›", 13, UiKit.BRAND, true));
            }
            page.addView(card);
        }
        if (page.getChildCount() == 1) page.addView(ui.empty("暂无可直接发起的通用流程"));
        host.show(ui.scroll(page));
    }
}
