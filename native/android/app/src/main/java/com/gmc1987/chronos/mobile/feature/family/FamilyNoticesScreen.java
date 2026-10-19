package com.gmc1987.chronos.mobile.feature.family;

import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Spinner;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ErrorMessages;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import org.json.JSONArray;
import org.json.JSONObject;

public final class FamilyNoticesScreen {
    private final android.app.Activity activity;
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final FamilyRepository repository;
    private final Runnable back;
    private JSONArray children = new JSONArray();
    private JSONArray notices = new JSONArray();
    private String selectedStudentId = "";

    public FamilyNoticesScreen(android.app.Activity activity, UiKit ui, AsyncRunner async, ScreenHost host,
            FamilyRepository repository, Runnable back) {
        this.activity = activity; this.ui = ui; this.async = async; this.host = host;
        this.repository = repository; this.back = back;
    }

    public void show() {
        host.setBackAction(back);
        LinearLayout page = ui.page("家校通知");
        LinearLayout body = ui.vertical();
        body.addView(ui.centered("正在加载…", true));
        page.addView(body);
        host.show(ui.scroll(page));
        async.run(repository::children, response -> {
            children = response.optJSONArray("data") == null
                    ? new JSONArray() : response.optJSONArray("data");
            render(body);
        }, error -> { });
        async.run(repository::notices, response -> {
            notices = response.optJSONArray("data") == null
                    ? new JSONArray() : response.optJSONArray("data");
            render(body);
        }, error -> {
            body.removeAllViews();
            body.addView(ui.error(ErrorMessages.display(error, "家校通知加载失败"), this::show));
        });
    }

    private void render(LinearLayout body) {
        body.removeAllViews();
        if (children.length() > 0) {
            java.util.List<String> names = new java.util.ArrayList<>();
            names.add("全部子女");
            for (int i = 0; i < children.length(); i++) {
                JSONObject child = children.optJSONObject(i);
                names.add(child == null ? "学生" : child.optString("studentName", "学生"));
            }
            Spinner selector = new Spinner(body.getContext());
            selector.setAdapter(new ArrayAdapter<>(body.getContext(),
                    android.R.layout.simple_spinner_dropdown_item, names));
            selector.setContentDescription("按子女筛选家校通知");
            int selectedPosition = 0;
            for (int i = 0; i < children.length(); i++) {
                JSONObject child = children.optJSONObject(i);
                if (child != null && selectedStudentId.equals(child.optString("id"))) {
                    selectedPosition = i + 1;
                    break;
                }
            }
            selector.setSelection(selectedPosition, false);
            selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, android.view.View view,
                        int position, long id) {
                    JSONObject child = position == 0 ? null : children.optJSONObject(position - 1);
                    String value = child == null ? "" : child.optString("id", "");
                    if (!value.equals(selectedStudentId)) {
                        selectedStudentId = value;
                        render(body);
                    }
                }
                @Override public void onNothingSelected(AdapterView<?> parent) { }
            });
            body.addView(selector);
        }
        JSONArray values = notices;
        int rendered = 0;
        for (int i = 0; i < values.length(); i++) {
            JSONObject notice = values.optJSONObject(i);
            if (notice == null) continue;
            if (!selectedStudentId.isEmpty()
                    && !selectedStudentId.equals(notice.optString("studentId"))) continue;
            LinearLayout card = ui.card();
            card.addView(ui.label(notice.optString("title", "家校通知"), 17, UiKit.TEXT, true));
            card.addView(ui.label(notice.optString("content", "暂无内容"), 14, UiKit.TEXT, false));
            card.addView(ui.label(notice.optString("publishAt", ""), 12, UiKit.MUTED, false));
            if (notice.optBoolean("receiptRequired")) {
                if ("RECEIVED".equals(notice.optString("receiptStatus"))) {
                    card.addView(ui.label("已回执", 13, UiKit.BRAND, true));
                } else if (notice.optBoolean("expired")) {
                    card.addView(ui.label("已过期，无法回执", 13, UiKit.MUTED, false));
                } else {
                    Button receipt = ui.primaryButton("确认收到");
                    receipt.setOnClickListener(view -> receipt(notice.optString("id")));
                    card.addView(receipt);
                }
            }
            body.addView(card);
            rendered++;
        }
        if (rendered == 0) body.addView(ui.empty("当前没有家校通知"));
    }

    private void receipt(String id) {
        async.run(() -> repository.receipt(id), ignored -> show(),
                error -> android.widget.Toast.makeText(
                        activity,
                        ErrorMessages.display(error, "回执失败"),
                        android.widget.Toast.LENGTH_LONG).show());
    }
}
