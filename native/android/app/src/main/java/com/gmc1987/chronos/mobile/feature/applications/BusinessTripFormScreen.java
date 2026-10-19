package com.gmc1987.chronos.mobile.feature.applications;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import java.util.Calendar;
import org.json.JSONObject;

/** Native business trip form with itinerary and budget fields. */
public final class BusinessTripFormScreen {
    private final Activity activity;
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final HttpClient http;
    private final Runnable back;

    public BusinessTripFormScreen(Activity activity, UiKit ui, AsyncRunner async, ScreenHost host,
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
        LinearLayout page = chrome.page("发起出差", back);
        LinearLayout itinerary = ui.card();
        itinerary.addView(ui.label("行程", 18, UiKit.TEXT, true));
        EditText destination = ui.input("出差地点", false);
        itinerary.addView(destination);
        Calendar now = Calendar.getInstance();
        NativeDateField start = new NativeDateField(activity, ui, "开始日期", now, false);
        NativeDateField end = new NativeDateField(activity, ui, "结束日期", now, false);
        itinerary.addView(start.view());
        itinerary.addView(end.view());
        EditText purpose = ui.multilineInput("出差事由", 3);
        itinerary.addView(purpose);
        page.addView(itinerary);

        LinearLayout budget = ui.card();
        budget.addView(ui.label("预算（人民币）", 18, UiKit.TEXT, true));
        EditText transport = amount("交通费");
        EditText accommodation = amount("住宿费");
        EditText meal = amount("餐费");
        EditText other = amount("其他费用");
        budget.addView(transport); budget.addView(accommodation);
        budget.addView(meal); budget.addView(other);
        page.addView(budget);

        Button submit = ui.primaryButton("提交出差申请");
        submit.setOnClickListener(view -> submit(destination, purpose, start, end,
                transport, accommodation, meal, other, submit));
        page.addView(submit);
        host.show(ui.scroll(page));
    }

    private EditText amount(String hint) {
        EditText field = ui.input(hint, false);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        field.setText("0");
        return field;
    }

    private void submit(EditText destination, EditText purpose, NativeDateField start,
            NativeDateField end, EditText transport, EditText accommodation, EditText meal,
            EditText other, Button button) {
        String place = destination.getText().toString().trim();
        String note = purpose.getText().toString().trim();
        if (place.isEmpty() || note.isEmpty()) {
            new ApplicationScreenChrome(ui, host).dialog("请完善表单", "请填写出差地点和事由");
            return;
        }
        if (end.millis() < start.millis()) {
            new ApplicationScreenChrome(ui, host).dialog("请完善表单", "结束日期不能早于开始日期");
            return;
        }
        try {
            JSONObject body = new JSONObject().put("destination", place)
                    .put("startDate", start.date()).put("endDate", end.date())
                    .put("purpose", note).put("currency", "CNY")
                    .put("transportAmount", number(transport)).put("accommodationAmount", number(accommodation))
                    .put("mealAmount", number(meal)).put("otherAmount", number(other));
            button.setEnabled(false);
            async.run(() -> http.post("/portal/collaboration/business-trips", body, true), response ->
                    new AlertDialog.Builder(activity).setTitle("提交成功")
                            .setMessage("出差申请已进入审批流程。")
                            .setPositiveButton("完成", (dialog, which) -> back.run()).show(), error -> {
                                button.setEnabled(true);
                                new ApplicationScreenChrome(ui, host).dialog("提交失败",
                                        ApplicationJson.message(error));
                            });
        } catch (Exception error) {
            new ApplicationScreenChrome(ui, host).dialog("请完善表单",
                    error instanceof NumberFormatException
                            ? "预算金额必须是有效数字" : ApplicationJson.message(error));
        }
    }

    private double number(EditText field) {
        String value = field.getText().toString().trim();
        return value.isEmpty() ? 0 : Double.parseDouble(value);
    }
}
