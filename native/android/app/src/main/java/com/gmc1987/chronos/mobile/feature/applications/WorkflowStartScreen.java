package com.gmc1987.chronos.mobile.feature.applications;

import android.app.Activity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import java.util.UUID;
import org.json.JSONObject;

/** Native dynamic workflow start form. */
public final class WorkflowStartScreen {
    private final Activity activity; private final UiKit ui; private final AsyncRunner async;
    private final ScreenHost host; private final HttpClient http; private final String flowId;
    private final Runnable back;

    public WorkflowStartScreen(Activity activity, UiKit ui, AsyncRunner async, ScreenHost host,
            HttpClient http, String flowId, Runnable back) {
        this.activity = activity; this.ui = ui; this.async = async; this.host = host;
        this.http = http; this.flowId = flowId; this.back = back;
    }

    public void show() {
        ApplicationScreenChrome chrome = new ApplicationScreenChrome(ui, host);
        chrome.loading("正在加载流程表单…");
        async.run(() -> ApplicationJson.dataObject(http.get("/workflows/" + flowId + "/start-form")),
                this::render,
                error -> chrome.error("发起流程", error, this::show, back));
    }

    private void render(JSONObject schema) {
        ApplicationScreenChrome chrome = new ApplicationScreenChrome(ui, host);
        LinearLayout page = chrome.page(schema.optString("flowName", "发起流程"), back);
        EditText businessKey = ui.input("业务编号（可选）", false);
        page.addView(businessKey);
        DynamicWorkflowForm form = new DynamicWorkflowForm(
                activity, ui, schema.optJSONArray("fields"), new JSONObject());
        page.addView(form.view());
        Button submit = ui.primaryButton("提交申请");
        submit.setOnClickListener(view -> submit(businessKey, form, submit));
        page.addView(submit);
        host.show(ui.scroll(page));
    }

    private void submit(EditText businessKey, DynamicWorkflowForm form, Button submit) {
        ApplicationScreenChrome chrome = new ApplicationScreenChrome(ui, host);
        try {
            JSONObject values = form.values(true);
            JSONObject body = new JSONObject()
                    .put("idempotencyKey", UUID.randomUUID().toString())
                    .put("businessKey", businessKey.getText().toString().trim())
                    .put("formData", values).put("variablesJson", values.toString());
            submit.setEnabled(false);
            async.run(() -> http.post("/workflows/" + flowId + "/start", body, true),
                    ignored -> chrome.message("提交成功", "流程申请已经提交。", back),
                    error -> { submit.setEnabled(true); chrome.dialog("提交失败",
                            ApplicationJson.message(error)); });
        } catch (IllegalArgumentException error) {
            chrome.dialog("请完善表单", error.getMessage());
        } catch (Exception error) {
            chrome.dialog("提交失败", ApplicationJson.message(error));
        }
    }
}
