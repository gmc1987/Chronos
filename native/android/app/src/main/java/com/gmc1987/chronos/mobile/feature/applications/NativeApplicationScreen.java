package com.gmc1987.chronos.mobile.feature.applications;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Renders portal applications with Android controls. The server supplied feature code selects a
 * native screen and APIs provide its data.
 */
public final class NativeApplicationScreen {
    private final Activity activity;
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final HttpClient http;
    private final JSONObject application;
    private final Runnable back;

    public NativeApplicationScreen(Activity activity, UiKit ui, AsyncRunner async, ScreenHost host,
            HttpClient http, JSONObject application, Runnable back) {
        this.activity = activity;
        this.ui = ui;
        this.async = async;
        this.host = host;
        this.http = http;
        this.application = application;
        this.back = back;
    }

    public void show() {
        if ("EXTERNAL".equalsIgnoreCase(application.optString("presentationMode"))
                || "EXTERNAL".equalsIgnoreCase(application.optString("openMode"))) {
            String route = application.optString("routePath");
            if (!route.isEmpty()) activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(route)));
            back.run();
            return;
        }
        String feature = application.optString("nativeFeatureCode");
        if (feature.isEmpty()) feature = fallbackFeature(application.optString("code"));
        switch (feature.toUpperCase(Locale.ROOT)) {
            case "WORKFLOW_TASKS": workflowCenter(); break;
            case "COLLABORATION": moduleHub("协同办公", collaborationModules()); break;
            case "DOCUMENTS": records("公文中心", "/portal/collaboration/documents/my", "暂无公文", back); break;
            case "FILES": records("文件中心", "/portal/collaboration/files/shares", "暂无共享文件", back); break;
            case "MEETINGS": records("我的会议", "/portal/education/meetings", "暂无会议", back); break;
            case "BUSINESS_TRIPS": records("我的出差", "/portal/collaboration/business-trips", "暂无出差申请", back); break;
            case "KNOWLEDGE": records("知识中心", "/admin/knowledge-bases?page=0&size=50", "暂无知识库", back); break;
            case "AI_CENTER": records("AI 智能中心", "/agent/specs/list?page=0&size=50", "暂无智能体", back); break;
            case "TEACHING_CENTER": moduleHub("教学中心", teachingModules()); break;
            default: showMessage(application.optString("name", "应用"), "该应用尚未配置原生功能标识。", back); break;
        }
    }

    public void showWorkflowInstance(String instanceId) {
        workflowInstance(instanceId, "待办任务", "/workflow-tasks/pending?page=0&size=50", "暂无待办任务");
    }

    private void workflowCenter() {
        LinearLayout page = page("流程中心", back);
        LinearLayout tabs = ui.horizontal();
        addAction(tabs, "待办", () -> workflowRecords("待办任务", "/workflow-tasks/pending?page=0&size=50", "暂无待办任务"));
        addAction(tabs, "已办", () -> workflowRecords("已办任务", "/workflow-tasks/handled?page=0&size=50", "暂无已办任务"));
        addAction(tabs, "我发起", () -> workflowRecords("我发起的", "/workflow-instances/initiated?page=0&size=50", "暂无发起记录"));
        addAction(tabs, "发起", this::availableWorkflows);
        page.addView(tabs);
        page.addView(ui.label("请选择上方分类查看流程数据。", 14, UiKit.MUTED, false));
        host.show(ui.scroll(page));
    }

    private void workflowRecords(String title, String path, String empty) {
        loadRecords(title, path, empty, this::workflowCenter, (row, card) -> {
            String instanceId = row.optString("instanceId", row.optString("processInstanceId", row.optString("id")));
            if (!instanceId.isEmpty()) {
                card.setClickable(true);
                card.setOnClickListener(view -> workflowInstance(instanceId, title, path, empty));
                card.addView(ui.label("查看流程详情  ›", 13, UiKit.BRAND, true));
            }
        });
    }

    private void availableWorkflows() {
        loadRecords("发起流程", "/workflows/available", "暂无可发起流程", this::workflowCenter,
                (row, card) -> {
                    String flowId = row.optString("id");
                    if (!flowId.isEmpty()) {
                        card.setClickable(true);
                        card.setOnClickListener(view -> workflowStart(flowId));
                        card.addView(ui.label("填写申请  ›", 13, UiKit.BRAND, true));
                    }
                });
    }

    private void workflowStart(String flowId) {
        showLoading("正在加载流程表单…");
        async.run(() -> dataObject(http.get("/workflows/" + flowId + "/start-form")),
                schema -> renderStartForm(flowId, schema),
                error -> showError("发起流程", error, () -> workflowStart(flowId), this::availableWorkflows));
    }

    private void renderStartForm(String flowId, JSONObject schema) {
        LinearLayout page = page(schema.optString("flowName", "发起流程"), this::availableWorkflows);
        EditText businessKey = ui.input("业务编号（可选）", false);
        page.addView(businessKey);
        DynamicForm form = buildDynamicForm(schema.optJSONArray("fields"), new JSONObject());
        page.addView(form.view);
        Button submit = ui.primaryButton("提交申请");
        submit.setOnClickListener(view -> {
            try {
                JSONObject values = form.values(true);
                submit.setEnabled(false);
                JSONObject body = new JSONObject()
                        .put("idempotencyKey", UUID.randomUUID().toString())
                        .put("businessKey", businessKey.getText().toString().trim())
                        .put("formData", values)
                        .put("variablesJson", values.toString());
                async.run(() -> http.post("/workflows/" + flowId + "/start", body, true),
                        ignored -> showMessage("提交成功", "流程申请已经提交。", this::workflowCenter),
                        error -> { submit.setEnabled(true); showDialog("提交失败", error.getMessage()); });
            } catch (IllegalArgumentException error) {
                showDialog("请完善表单", error.getMessage());
            } catch (Exception error) {
                showDialog("提交失败", error.getMessage());
            }
        });
        page.addView(submit);
        host.show(ui.scroll(page));
    }

    private void workflowInstance(String instanceId, String sourceTitle, String sourcePath, String sourceEmpty) {
        showLoading("正在加载流程详情…");
        async.run(() -> dataObject(http.get("/workflow-instances/" + instanceId + "/forms")),
                bundle -> renderWorkflowInstance(instanceId, bundle,
                        () -> workflowRecords(sourceTitle, sourcePath, sourceEmpty)),
                error -> showError("流程详情", error,
                        () -> workflowInstance(instanceId, sourceTitle, sourcePath, sourceEmpty),
                        () -> workflowRecords(sourceTitle, sourcePath, sourceEmpty)));
    }

    private void renderWorkflowInstance(String instanceId, JSONObject bundle, Runnable goBack) {
        JSONObject flow = bundle.optJSONObject("flow");
        LinearLayout page = page(flow == null ? "流程详情" : flow.optString("flowName", "流程详情"), goBack);
        JSONArray forms = bundle.optJSONArray("forms");
        List<FormSection> sections = new ArrayList<>();
        if (forms != null) for (int i = 0; i < forms.length(); i++) {
            JSONObject formJson = forms.optJSONObject(i);
            if (formJson == null) continue;
            page.addView(ui.label(formJson.optString("formName", "流程表单"), 18, UiKit.TEXT, true));
            DynamicForm dynamic = buildDynamicForm(formJson.optJSONArray("fields"), formJson.optJSONObject("data"));
            page.addView(dynamic.view);
            sections.add(new FormSection(formJson.optString("formId"), dynamic));
        }
        JSONArray history = bundle.optJSONArray("history");
        if (history != null && history.length() > 0) {
            page.addView(ui.label("审批记录", 18, UiKit.TEXT, true));
            for (int i = 0; i < history.length(); i++) page.addView(recordCard(history.optJSONObject(i)));
        }
        JSONObject task = bundle.optJSONObject("currentTask");
        if (task != null) {
            EditText comment = ui.input("处理意见", false);
            page.addView(comment);
            LinearLayout actions = ui.horizontal();
            JSONObject operations = bundle.optJSONObject("operations");
            if ("STARTER_REWORK".equals(task.optString("taskKind"))) {
                Button resubmit = ui.primaryButton("重新提交");
                resubmit.setOnClickListener(view -> finishTask(instanceId, task.optString("id"), "resubmit",
                        comment.getText().toString(), sections, goBack));
                actions.addView(resubmit, new LinearLayout.LayoutParams(0, ui.dp(54), 1));
            } else {
                if (operations == null || operations.optBoolean("approve", true)) {
                    Button approve = ui.primaryButton("同意");
                    approve.setOnClickListener(view -> finishTask(instanceId, task.optString("id"), "approve",
                            comment.getText().toString(), sections, goBack));
                    actions.addView(approve, new LinearLayout.LayoutParams(0, ui.dp(54), 1));
                }
                if (operations == null || operations.optBoolean("reject", true)) {
                    Button reject = ui.destructiveButton("驳回");
                    reject.setOnClickListener(view -> finishTask(instanceId, task.optString("id"), "reject",
                            comment.getText().toString(), sections, goBack));
                    actions.addView(reject, new LinearLayout.LayoutParams(0, ui.dp(54), 1));
                }
            }
            page.addView(actions);
        }
        host.show(ui.scroll(page));
    }

    private void finishTask(String instanceId, String taskId, String action, String comment,
            List<FormSection> sections, Runnable goBack) {
        if (taskId.isEmpty()) return;
        showLoading("reject".equals(action) ? "正在驳回流程…" : "正在提交流程…");
        async.run(() -> {
            // Persist every editable runtime form before moving the workflow task forward.
            for (FormSection section : sections) {
                if (!section.id.isEmpty()) {
                    JSONObject body = new JSONObject().put("data", section.form.editableValues()).put("draft", false);
                    http.put("/workflow-instances/" + instanceId + "/forms/" + section.id, body);
                }
            }
            JSONObject body = new JSONObject().put("comment", comment);
            if ("approve".equals(action)) body.put("approved", true);
            return http.post("/workflow-tasks/" + taskId + "/" + ("approve".equals(action) ? "complete" : action), body, true);
        }, ignored -> showMessage("reject".equals(action) ? "已驳回" : "处理完成", "流程状态已经更新。", goBack),
                error -> showError("处理失败", error,
                        () -> workflowInstance(instanceId, "待办任务", "/workflow-tasks/pending?page=0&size=50", "暂无待办任务"), goBack));
    }

    private void moduleHub(String title, List<Module> modules) {
        LinearLayout page = page(title, back);
        for (Module module : modules) {
            LinearLayout card = ui.card();
            card.addView(ui.label(module.title, 18, UiKit.TEXT, true));
            card.addView(ui.label(module.detail, 13, UiKit.MUTED, false));
            card.setClickable(true);
            card.setOnClickListener(view -> records(module.title, module.path, "暂无" + module.title + "数据",
                    () -> moduleHub(title, modules)));
            page.addView(card);
        }
        host.show(ui.scroll(page));
    }

    private void records(String title, String path, String empty, Runnable goBack) {
        loadRecords(title, path, empty, goBack, (row, card) -> {
            card.setClickable(true);
            card.setOnClickListener(view -> recordDetail(title, row,
                    () -> records(title, path, empty, goBack)));
            card.addView(ui.label("查看详情  ›", 13, UiKit.BRAND, true));
        });
    }

    private void recordDetail(String title, JSONObject row, Runnable goBack) {
        LinearLayout page = page(title + "详情", goBack);
        appendDetail(page, row, "");
        host.show(ui.scroll(page));
    }

    private void appendDetail(LinearLayout page, Object value, String prefix) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                appendDetail(page, object.opt(key), prefix.isEmpty() ? label(key) : prefix + " · " + label(key));
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            if (array.length() == 0) page.addView(detailRow(prefix, "暂无"));
            for (int i = 0; i < array.length(); i++) appendDetail(page, array.opt(i), prefix + " " + (i + 1));
        } else {
            page.addView(detailRow(prefix, value == null || value == JSONObject.NULL ? "—" : String.valueOf(value)));
        }
    }

    private View detailRow(String key, String value) {
        LinearLayout card = ui.card();
        card.addView(ui.label(key, 13, UiKit.MUTED, false));
        TextView content = ui.label(value, 15, UiKit.TEXT, false);
        content.setTextIsSelectable(true);
        card.addView(content);
        return card;
    }

    private void loadRecords(String title, String path, String empty, Runnable goBack, CardDecorator decorator) {
        showLoading("正在加载…");
        async.run(() -> rows(http.get(path)), rows -> {
            LinearLayout page = page(title, goBack);
            if (rows.length() == 0) page.addView(ui.empty(empty));
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                LinearLayout card = recordCard(row);
                decorator.decorate(row, card);
                page.addView(card);
            }
            host.show(ui.scroll(page));
        }, error -> showError(title, error, () -> loadRecords(title, path, empty, goBack, decorator), goBack));
    }

    private LinearLayout recordCard(JSONObject source) {
        JSONObject row = source.optJSONObject("meeting");
        if (row == null) row = source;
        LinearLayout card = ui.card();
        String[] preferred = {"flowName", "title", "name", "purpose", "documentTitle", "fileName",
                "status", "nodeName", "initiator", "startTime", "endTime", "createTime", "description"};
        int shown = 0;
        for (String key : preferred) {
            if (!row.has(key) || row.isNull(key)) continue;
            String value = String.valueOf(row.opt(key));
            if (value.isEmpty() || "null".equals(value)) continue;
            TextView text = ui.label((shown == 0 ? "" : label(key) + "：") + value,
                    shown == 0 ? 17 : 13, shown == 0 ? UiKit.TEXT : UiKit.MUTED, shown == 0);
            card.addView(text);
            if (++shown >= 7) break;
        }
        if (shown == 0) card.addView(ui.label("业务记录", 17, UiKit.TEXT, true));
        return card;
    }

    private DynamicForm buildDynamicForm(JSONArray fields, JSONObject initial) {
        LinearLayout container = ui.vertical();
        Map<String, FieldReader> readers = new LinkedHashMap<>();
        if (fields == null) return new DynamicForm(container, readers);
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.optJSONObject(i);
            if (field == null) continue;
            String key = field.optString("fieldKey");
            if (key.isEmpty()) continue;
            String title = field.optString("fieldLabel", key) + (field.optBoolean("required") ? " *" : "");
            String type = field.optString("fieldType", "TEXT").toUpperCase(Locale.ROOT);
            boolean readOnly = "READ".equalsIgnoreCase(field.optString("permission"));
            Object old = initial == null ? null : initial.opt(key);
            container.addView(ui.label(title, 13, UiKit.MUTED, false));
            if ("BOOLEAN".equals(type)) {
                Switch control = new Switch(activity); control.setText(title); control.setChecked(asBoolean(old)); control.setEnabled(!readOnly);
                container.addView(control); readers.put(key, new FieldReader(field, control::isChecked));
            } else if ("SELECT".equals(type) || "RADIO".equals(type)) {
                List<Option> options = options(field); List<String> labels = new ArrayList<>(); labels.add("请选择");
                for (Option option : options) labels.add(option.label);
                Spinner spinner = new Spinner(activity); spinner.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, labels)); spinner.setEnabled(!readOnly);
                String current = old == null ? "" : String.valueOf(old); for (int n = 0; n < options.size(); n++) if (options.get(n).value.equals(current)) spinner.setSelection(n + 1);
                container.addView(spinner); readers.put(key, new FieldReader(field, () -> spinner.getSelectedItemPosition() == 0 ? "" : options.get(spinner.getSelectedItemPosition() - 1).value));
            } else if ("CHECKBOX".equals(type)) {
                LinearLayout group = ui.vertical(); List<CheckBox> checks = new ArrayList<>(); JSONArray selected = old instanceof JSONArray ? (JSONArray) old : new JSONArray();
                for (Option option : options(field)) { CheckBox check = new CheckBox(activity); check.setText(option.label); check.setTag(option.value); check.setChecked(contains(selected, option.value)); check.setEnabled(!readOnly); group.addView(check); checks.add(check); }
                container.addView(group); readers.put(key, new FieldReader(field, () -> { JSONArray result = new JSONArray(); for (CheckBox check : checks) if (check.isChecked()) result.put(String.valueOf(check.getTag())); return result; }));
            } else if ("FILE".equals(type)) {
                JSONArray attachments = old instanceof JSONArray ? (JSONArray) old : new JSONArray();
                container.addView(ui.label(attachments.length() == 0 ? "暂无附件" : attachments.length() + " 个附件", 14, UiKit.TEXT, false));
                readers.put(key, new FieldReader(field, () -> attachments));
            } else {
                EditText input = ui.input(title, false); input.setText(old == null || old == JSONObject.NULL ? "" : String.valueOf(old)); input.setEnabled(!readOnly);
                if ("TEXTAREA".equals(type)) { input.setSingleLine(false); input.setMinLines(4); }
                if ("NUMBER".equals(type)) input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
                container.addView(input); readers.put(key, new FieldReader(field, () -> input.getText().toString().trim()));
            }
        }
        return new DynamicForm(container, readers);
    }

    private LinearLayout page(String title, Runnable goBack) {
        LinearLayout page = ui.page(title);
        Button button = ui.secondaryButton("‹ 返回");
        button.setOnClickListener(view -> goBack.run());
        page.addView(button, 0, new LinearLayout.LayoutParams(-1, ui.dp(44)));
        return page;
    }

    private void showLoading(String text) { host.show(ui.centered(text, true)); }
    private void showError(String title, Exception error, Runnable retry, Runnable goBack) {
        LinearLayout page = page(title, goBack); page.addView(ui.error(message(error), retry)); host.show(ui.scroll(page));
    }
    private void showMessage(String title, String message, Runnable done) {
        LinearLayout page = page(title, done); page.addView(ui.empty(message)); host.show(ui.scroll(page));
    }
    private void showDialog(String title, String message) { new AlertDialog.Builder(activity).setTitle(title).setMessage(message).setPositiveButton("知道了", null).show(); }
    private void addAction(LinearLayout row, String title, Runnable action) { Button button = ui.secondaryButton(title); button.setOnClickListener(v -> action.run()); row.addView(button, new LinearLayout.LayoutParams(0, ui.dp(46), 1)); }

    private static JSONObject dataObject(JSONObject envelope) { JSONObject data = envelope.optJSONObject("data"); return data == null ? envelope : data; }
    private static JSONArray rows(JSONObject envelope) {
        Object data = envelope.opt("data"); if (data instanceof JSONArray) return (JSONArray) data;
        JSONObject object = data instanceof JSONObject ? (JSONObject) data : envelope;
        for (String key : new String[]{"content", "items", "records", "data"}) { JSONArray result = object.optJSONArray(key); if (result != null) return result; }
        return new JSONArray();
    }
    private static String fallbackFeature(String code) {
        Map<String, String> map = new LinkedHashMap<>(); map.put("workflow", "WORKFLOW_TASKS"); map.put("oa", "COLLABORATION"); map.put("document", "DOCUMENTS"); map.put("file", "FILES"); map.put("EDUCATION_MEETINGS", "MEETINGS"); map.put("STAFF_BUSINESS_TRIP", "BUSINESS_TRIPS"); map.put("knowledge", "KNOWLEDGE"); map.put("ai", "AI_CENTER"); map.put("teaching-center", "TEACHING_CENTER"); return map.getOrDefault(code, code);
    }
    private static String label(String key) { Map<String, String> map = new LinkedHashMap<>(); map.put("status", "状态"); map.put("nodeName", "节点"); map.put("initiator", "发起人"); map.put("startTime", "开始"); map.put("endTime", "结束"); map.put("createTime", "创建时间"); map.put("description", "说明"); map.put("purpose", "事由"); return map.getOrDefault(key, key); }
    private static String message(Exception error) { return error.getMessage() == null ? "请求失败，请稍后重试" : error.getMessage(); }
    private static boolean asBoolean(Object value) { return value instanceof Boolean ? (Boolean) value : "true".equalsIgnoreCase(String.valueOf(value)); }
    private static boolean contains(JSONArray array, String value) { for (int i = 0; i < array.length(); i++) if (value.equals(array.optString(i))) return true; return false; }
    private static List<Option> options(JSONObject field) { List<Option> result = new ArrayList<>(); String raw = field.optString("optionsJson"); try { JSONArray values = new JSONArray(raw); for (int i = 0; i < values.length(); i++) { JSONObject item = values.optJSONObject(i); if (item != null) result.add(new Option(String.valueOf(item.opt("value")), String.valueOf(item.opt("label")))); } } catch (Exception ignored) {} return result; }

    private static List<Module> collaborationModules() { List<Module> result = new ArrayList<>(); result.add(new Module("用车与用印", "申请与执行状态", "/portal/collaboration/requests/my")); result.add(new Module("出差", "行程、预算和审批", "/portal/collaboration/business-trips")); result.add(new Module("公文", "收发、签发和归档", "/portal/collaboration/documents/my")); result.add(new Module("共享文件", "统一文件共享", "/portal/collaboration/files/shares")); result.add(new Module("通讯录", "教职工联系方式", "/portal/collaboration/contacts?keyword=")); return result; }
    private static List<Module> teachingModules() { String[][] values = {{"plans","教学计划"},{"lesson-plans","教案"},{"preparations","集体备课"},{"coursewares","课件"},{"materials","教学材料"},{"questions","题库"},{"knowledge-points","知识点"},{"research","教研"},{"mistakes","错题沉淀"}}; List<Module> result = new ArrayList<>(); for (String[] value : values) result.add(new Module(value[1], "查看和管理" + value[1], "/education/teaching-center/api/" + value[0] + "?page=0&size=50")); return result; }

    private interface CardDecorator { void decorate(JSONObject row, LinearLayout card); }
    private interface ValueProvider { Object get(); }
    private static final class FieldReader { final JSONObject field; final ValueProvider provider; FieldReader(JSONObject field, ValueProvider provider) { this.field = field; this.provider = provider; } }
    private static final class DynamicForm {
        final LinearLayout view;
        final Map<String, FieldReader> readers;
        DynamicForm(LinearLayout view, Map<String, FieldReader> readers) { this.view = view; this.readers = readers; }
        JSONObject values(boolean validate) { return collect(validate, false); }
        JSONObject editableValues() { return collect(false, true); }
        private JSONObject collect(boolean validate, boolean editableOnly) {
            JSONObject values = new JSONObject();
            for (Map.Entry<String, FieldReader> entry : readers.entrySet()) {
                if (editableOnly && !"EDIT".equalsIgnoreCase(entry.getValue().field.optString("permission"))) continue;
                Object value = entry.getValue().provider.get();
                boolean empty = value == null || "".equals(value)
                        || value instanceof JSONArray && ((JSONArray) value).length() == 0;
                if (validate && entry.getValue().field.optBoolean("required") && empty) {
                    throw new IllegalArgumentException("请填写“"
                            + entry.getValue().field.optString("fieldLabel", entry.getKey()) + "”");
                }
                try { values.put(entry.getKey(), value == null ? JSONObject.NULL : value); }
                catch (Exception ignored) {}
            }
            return values;
        }
    }
    private static final class FormSection { final String id; final DynamicForm form; FormSection(String id, DynamicForm form) { this.id = id; this.form = form; } }
    private static final class Option { final String value, label; Option(String value, String label) { this.value = value; this.label = label; } }
    private static final class Module { final String title, detail, path; Module(String title, String detail, String path) { this.title = title; this.detail = detail; this.path = path; } }
}
