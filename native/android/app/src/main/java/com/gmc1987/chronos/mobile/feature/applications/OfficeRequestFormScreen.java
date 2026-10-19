package com.gmc1987.chronos.mobile.feature.applications;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native vehicle/seal request form backed by the configured office resources. */
public final class OfficeRequestFormScreen {
    private final Activity activity;
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final HttpClient http;
    private final Runnable back;

    public OfficeRequestFormScreen(Activity activity, UiKit ui, AsyncRunner async, ScreenHost host,
            HttpClient http, Runnable back) {
        this.activity = activity;
        this.ui = ui;
        this.async = async;
        this.host = host;
        this.http = http;
        this.back = back;
    }

    public void show() {
        ApplicationScreenChrome chrome = new ApplicationScreenChrome(ui, host);
        chrome.loading("正在加载可用资源…");
        async.run(() -> new JSONObject()
                        .put("VEHICLE", ApplicationJson.rows(
                                http.get("/portal/collaboration/resources?type=VEHICLE")))
                        .put("SEAL", ApplicationJson.rows(
                                http.get("/portal/collaboration/resources?type=SEAL"))),
                this::render,
                error -> chrome.error("发起用车/用印", error, this::show, back));
    }

    private void render(JSONObject resourceData) {
        ApplicationScreenChrome chrome = new ApplicationScreenChrome(ui, host);
        LinearLayout page = chrome.page("发起用车/用印", back);
        LinearLayout basic = ui.card();
        basic.addView(ui.label("申请类型", 18, UiKit.TEXT, true));
        Spinner type = new Spinner(activity);
        type.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"用车", "用印"}));
        basic.addView(type);
        Spinner resource = new Spinner(activity);
        basic.addView(resource);
        Calendar startValue = Calendar.getInstance(); startValue.add(Calendar.HOUR_OF_DAY, 1);
        Calendar endValue = Calendar.getInstance(); endValue.add(Calendar.HOUR_OF_DAY, 2);
        NativeDateField start = new NativeDateField(activity, ui, "开始时间", startValue, true);
        NativeDateField end = new NativeDateField(activity, ui, "结束时间", endValue, true);
        basic.addView(start.view()); basic.addView(end.view());
        EditText purpose = ui.multilineInput("申请事由", 3);
        basic.addView(purpose);
        page.addView(basic);

        LinearLayout details = ui.card();
        page.addView(details);
        FormFields fields = new FormFields(details);
        Runnable refresh = () -> {
            String code = type.getSelectedItemPosition() == 0 ? "VEHICLE" : "SEAL";
            bindResources(resource, resourceData.optJSONArray(code));
            fields.render(code);
        };
        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refresh.run();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        refresh.run();

        Button submit = ui.primaryButton("提交申请");
        submit.setOnClickListener(view -> submit(type, resource, resourceData, start, end,
                purpose, fields, submit));
        page.addView(submit);
        host.show(ui.scroll(page));
    }

    private void bindResources(Spinner spinner, JSONArray values) {
        List<String> labels = new ArrayList<>();
        labels.add("请选择资源");
        if (values != null) for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i);
            if (item != null) labels.add(item.optString("resourceName", "未命名资源"));
        }
        spinner.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item, labels));
    }

    private void submit(Spinner type, Spinner resource, JSONObject resourceData,
            NativeDateField start, NativeDateField end, EditText purpose,
            FormFields fields, Button button) {
        String requestType = type.getSelectedItemPosition() == 0 ? "VEHICLE" : "SEAL";
        JSONArray resources = resourceData.optJSONArray(requestType);
        int selected = resource.getSelectedItemPosition() - 1;
        String note = purpose.getText().toString().trim();
        if (selected < 0 || resources == null || resources.optJSONObject(selected) == null) {
            new ApplicationScreenChrome(ui, host).dialog("请完善表单", "请选择可用资源");
            return;
        }
        if (note.isEmpty()) {
            new ApplicationScreenChrome(ui, host).dialog("请完善表单", "请填写申请事由");
            return;
        }
        if (end.millis() <= start.millis()) {
            new ApplicationScreenChrome(ui, host).dialog("请完善表单", "结束时间必须晚于开始时间");
            return;
        }
        try {
            JSONObject body = new JSONObject().put("requestType", requestType)
                    .put("resourceId", resources.optJSONObject(selected).optString("id"))
                    .put("startAt", start.dateTime()).put("endAt", end.dateTime())
                    .put("purpose", note);
            if ("VEHICLE".equals(requestType)) {
                String destination = fields.name.getText().toString().trim();
                if (destination.isEmpty()) {
                    new ApplicationScreenChrome(ui, host).dialog("请完善表单", "请填写用车目的地");
                    return;
                }
                body.put("destination", destination).put("passengerCount", fields.count());
                body.put("documentTitle", JSONObject.NULL).put("copyCount", JSONObject.NULL);
            } else {
                String title = fields.name.getText().toString().trim();
                if (title.isEmpty()) {
                    new ApplicationScreenChrome(ui, host).dialog("请完善表单", "请填写用印文件名称");
                    return;
                }
                body.put("documentTitle", title).put("copyCount", fields.count());
                body.put("destination", JSONObject.NULL).put("passengerCount", JSONObject.NULL);
            }
            button.setEnabled(false);
            async.run(() -> http.post("/portal/collaboration/requests", body, true), response ->
                    new AlertDialog.Builder(activity).setTitle("提交成功")
                            .setMessage("申请已进入审批流程。")
                            .setPositiveButton("完成", (dialog, which) -> back.run()).show(), error -> {
                                button.setEnabled(true);
                                new ApplicationScreenChrome(ui, host).dialog("提交失败",
                                        ApplicationJson.message(error));
                            });
        } catch (Exception error) {
            new ApplicationScreenChrome(ui, host).dialog("提交失败", ApplicationJson.message(error));
        }
    }

    private final class FormFields {
        private final LinearLayout container;
        private EditText name;
        private EditText quantity;

        FormFields(LinearLayout container) {
            this.container = container;
        }

        void render(String requestType) {
            container.removeAllViews();
            boolean vehicle = "VEHICLE".equals(requestType);
            container.addView(ui.label(vehicle ? "用车信息" : "用印信息", 18, UiKit.TEXT, true));
            name = ui.input(vehicle ? "目的地" : "文件名称", false);
            quantity = ui.input(vehicle ? "乘车人数" : "文件份数", false);
            quantity.setInputType(InputType.TYPE_CLASS_NUMBER);
            quantity.setText("1");
            container.addView(name); container.addView(quantity);
        }

        int count() {
            try { return Math.max(1, Integer.parseInt(quantity.getText().toString().trim())); }
            catch (Exception ignored) { return 1; }
        }
    }
}
