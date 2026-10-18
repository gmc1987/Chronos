package com.gmc1987.chronos.mobile.feature.applications;

import android.app.Activity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native workflow instance detail, runtime forms, history and task actions. */
public final class WorkflowInstanceScreen {
    private final Activity activity; private final UiKit ui; private final AsyncRunner async;
    private final ScreenHost host; private final HttpClient http; private final String instanceId;
    private final Runnable back;

    public WorkflowInstanceScreen(Activity activity, UiKit ui, AsyncRunner async, ScreenHost host,
            HttpClient http, String instanceId, Runnable back) {
        this.activity = activity; this.ui = ui; this.async = async; this.host = host;
        this.http = http; this.instanceId = instanceId; this.back = back;
    }

    public void show() {
        ApplicationScreenChrome chrome = new ApplicationScreenChrome(ui, host);
        chrome.loading("正在加载流程详情…");
        async.run(() -> ApplicationJson.dataObject(
                        http.get("/workflow-instances/" + instanceId + "/forms")),
                this::render,
                error -> chrome.error("流程详情", error, this::show, back));
    }

    private void render(JSONObject bundle) {
        JSONObject flow = bundle.optJSONObject("flow");
        String title = flow == null ? "流程详情" : flow.optString("flowName", "流程详情");
        LinearLayout page = new ApplicationScreenChrome(ui, host).page(title, back);
        JSONArray forms = bundle.optJSONArray("forms");
        List<FormSection> sections = new ArrayList<>();
        if (forms != null) for (int i = 0; i < forms.length(); i++) {
            JSONObject formJson = forms.optJSONObject(i); if (formJson == null) continue;
            page.addView(ui.label(formJson.optString("formName", "流程表单"),
                    18, UiKit.TEXT, true));
            DynamicWorkflowForm dynamic = new DynamicWorkflowForm(activity, ui,
                    formJson.optJSONArray("fields"), formJson.optJSONObject("data"));
            page.addView(dynamic.view());
            sections.add(new FormSection(formJson.optString("formId"), dynamic));
        }
        JSONArray history = bundle.optJSONArray("history");
        if (history != null && history.length() > 0) {
            page.addView(ui.label("审批记录", 18, UiKit.TEXT, true));
            NativeRecordViews records = new NativeRecordViews(ui);
            for (int i = 0; i < history.length(); i++) {
                JSONObject row = history.optJSONObject(i);
                if (row != null) page.addView(records.card(row));
            }
        }
        JSONObject task = bundle.optJSONObject("currentTask");
        if (task != null) addActions(page, bundle, task, sections);
        host.show(ui.scroll(page));
    }

    private void addActions(LinearLayout page, JSONObject bundle, JSONObject task,
            List<FormSection> sections) {
        EditText comment = ui.input("处理意见", false);
        page.addView(comment);
        LinearLayout actions = ui.horizontal();
        JSONObject operations = bundle.optJSONObject("operations");
        if ("STARTER_REWORK".equals(task.optString("taskKind"))) {
            addAction(actions, "重新提交", false, () -> finishTask(task.optString("id"),
                    "resubmit", comment.getText().toString(), sections));
        } else {
            if (operations == null || operations.optBoolean("approve", true)) {
                addAction(actions, "同意", false, () -> finishTask(task.optString("id"),
                        "approve", comment.getText().toString(), sections));
            }
            if (operations == null || operations.optBoolean("reject", true)) {
                addAction(actions, "驳回", true, () -> finishTask(task.optString("id"),
                        "reject", comment.getText().toString(), sections));
            }
        }
        page.addView(actions);
    }

    private void addAction(LinearLayout row, String title, boolean destructive, Runnable action) {
        Button button = destructive ? ui.destructiveButton(title) : ui.primaryButton(title);
        button.setOnClickListener(view -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(0, ui.dp(54), 1));
    }

    private void finishTask(String taskId, String action, String comment,
            List<FormSection> sections) {
        if (taskId.isEmpty()) return;
        ApplicationScreenChrome chrome = new ApplicationScreenChrome(ui, host);
        chrome.loading("reject".equals(action) ? "正在驳回流程…" : "正在提交流程…");
        async.run(() -> {
            for (FormSection section : sections) {
                if (!section.id.isEmpty()) {
                    JSONObject form = new JSONObject().put("data", section.form.editableValues())
                            .put("draft", false);
                    http.put("/workflow-instances/" + instanceId + "/forms/" + section.id, form);
                }
            }
            JSONObject body = new JSONObject().put("comment", comment);
            if ("approve".equals(action)) body.put("approved", true);
            String operation = "approve".equals(action) ? "complete" : action;
            return http.post("/workflow-tasks/" + taskId + "/" + operation, body, true);
        }, ignored -> chrome.message("reject".equals(action) ? "已驳回" : "处理完成",
                "流程状态已经更新。", back),
                error -> chrome.error("处理失败", error, this::show, back));
    }

    private static final class FormSection {
        final String id; final DynamicWorkflowForm form;
        FormSection(String id, DynamicWorkflowForm form) { this.id = id; this.form = form; }
    }
}
